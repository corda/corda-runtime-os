package net.corda.entityprocessor.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.persistence.DeleteEntities
import net.corda.data.persistence.DeleteEntitiesById
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntities
import net.corda.data.persistence.MergeEntities
import net.corda.data.persistence.PersistEntities
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.persistence.testkit.fake.FakeDbConnectionManager
import net.corda.db.persistence.testkit.helpers.BasicMocks
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.db.persistence.testkit.helpers.SandboxHelper.CAT_CLASS_NAME
import net.corda.db.persistence.testkit.helpers.SandboxHelper.DOG_CLASS_NAME
import net.corda.db.persistence.testkit.helpers.SandboxHelper.createCat
import net.corda.db.persistence.testkit.helpers.SandboxHelper.createCatKeyInstance
import net.corda.db.persistence.testkit.helpers.SandboxHelper.createDog
import net.corda.db.persistence.testkit.helpers.SandboxHelper.getCatClass
import net.corda.db.persistence.testkit.helpers.SandboxHelper.getDogClass
import net.corda.db.persistence.testkit.helpers.SandboxHelper.getOwnerClass
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.entityprocessor.impl.internal.PersistenceServiceInternal
import net.corda.entityprocessor.impl.internal.getClass
import net.corda.entityprocessor.impl.tests.helpers.AnimalCreator.createCats
import net.corda.entityprocessor.impl.tests.helpers.AnimalCreator.createDogs
import net.corda.messaging.api.records.Record
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.getSerializationService
import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.persistence.EntityManagerFactory

sealed class QuerySetup {
    data class NamedQuery(val params: Map<String, String>, val query: String = "Dog.summon") : QuerySetup()
    data class All(val className: String) : QuerySetup()
}

/**
 * To use Postgres rather than in-memory (HSQL):
 *
 *     docker run --rm --name test-instance -e POSTGRES_PASSWORD=password -p 5432:5432 postgres
 *
 *     gradlew integrationTest -PpostgresPort=5432
 *
 * Rather than creating a new serializer in these tests from scratch,
 * we grab a reference to the one in the sandbox and use that to serialize and de-serialize.
 */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceServiceInternalTests {
    private companion object {
        const val TOPIC = "pretend-topic"
        val EXTERNAL_EVENT_CONTEXT = ExternalEventContext("request id", "flow id", KeyValuePairList(emptyList()))
        val logger = contextLogger()
    }

    @InjectService
    lateinit var lbm: LiquibaseSchemaMigrator

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var responseFactory: ResponseFactory
    private lateinit var deserializer: CordaAvroDeserializer<EntityResponse>

    private lateinit var virtualNodeInfo: VirtualNodeInfo
    private lateinit var entitySandboxService: EntitySandboxService
    private lateinit var sandbox: SandboxGroupContext
    private lateinit var entityManagerFactory: EntityManagerFactory
    private lateinit var dogClass: Class<*>
    private lateinit var catClass: Class<*>
    private lateinit var schemaName: String

    @BeforeAll
    fun setup(
        @InjectService(timeout = 5000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        logger.info("Setup test (test Directory: $testDirectory)")
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(timeout = 10000)
            cpiInfoReadService = setup.fetchService(timeout = 10000)
            virtualNodeInfoReadService = setup.fetchService(timeout = 10000)
            responseFactory = setup.fetchService(timeout = 10000)
            deserializer = setup.fetchService<CordaAvroSerializationFactory>(timeout = 10000)
                .createAvroDeserializer({}, EntityResponse::class.java)
        }
    }

    @BeforeEach
    fun beforeEach() {
        virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)

        val testId = (0..1000000).random() // keeping this shorter than UUID.
        schemaName = "PSIT$testId"
        val animalDbConnection = Pair(virtualNodeInfo.vaultDmlConnectionId, "animals-node-$testId")
        val dbConnectionManager = FakeDbConnectionManager(listOf(animalDbConnection), schemaName)
        entitySandboxService = createEntitySandbox(dbConnectionManager)

        sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity)

        // migrate DB schema
        dogClass = sandbox.sandboxGroup.getDogClass()
        catClass = sandbox.sandboxGroup.getCatClass()
        val cl = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    dogClass.packageName, listOf("migration/db.changelog-master.xml"),
                    classLoader = dogClass.classLoader
                ),
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    catClass.packageName, listOf("migration/db.changelog-master.xml"),
                    classLoader = catClass.classLoader
                )
            )
        )

        lbm.updateDb(dbConnectionManager.getDataSource(animalDbConnection.first).connection, cl)

        entityManagerFactory = dbConnectionManager.createEntityManagerFactory(
            animalDbConnection.first,
            JpaEntitiesSet.create(
                animalDbConnection.second,
                setOf(dogClass, catClass, sandbox.sandboxGroup.getOwnerClass())
            )
        )

        // Each test is likely to leave junk lying around in the tables before the next test.
        // We can't trust deleting the tables because tests can run concurrently.
    }


    private fun noOpPayloadCheck(bytes: ByteBuffer) = bytes

    @Test
    fun `persist`() {
        val persistenceService = PersistenceServiceInternal(entitySandboxService::getClass, this::noOpPayloadCheck)
        val payload = PersistEntities(listOf(sandbox.serialize(sandbox.createDog("Rover").instance)))

        val entityManager = BasicMocks.entityManager()

        persistenceService.persist(sandbox.getSerializationService(), entityManager, payload)

        Mockito.verify(entityManager).persist(Mockito.any())
    }

    @Test
    fun `persist via message processor`() = assertPersistEntities(sandbox.createDog().instance)

    @Test
    fun `persist using two different sandboxes captures exception in response`() {
        // Having 2 different sandboxes requires setting up an additional virtual node and db connection manager
        val virtualNodeInfoTwo = virtualNode.load(Resources.FISH_CPB)

        val animalDbConnection = Pair(virtualNodeInfo.vaultDmlConnectionId, "animals-node")
        val calcDbConnection = Pair(virtualNodeInfoTwo.vaultDmlConnectionId, "calc-node")

        val myDbConnectionManager = FakeDbConnectionManager(
            listOf(animalDbConnection, calcDbConnection),
            "PSIT2"
        )

        val myEntitySandboxService = createEntitySandbox(myDbConnectionManager)

        val sandboxOne = myEntitySandboxService.get(virtualNodeInfo.holdingIdentity)

        // migrate DB schema
        val dogClass = sandboxOne.sandboxGroup.getDogClass()
        val cl = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    dogClass.packageName, listOf("migration/db.changelog-master.xml"),
                    classLoader = dogClass.classLoader
                ),
            )
        )
        lbm.updateDb(myDbConnectionManager.getDataSource(animalDbConnection.first).connection, cl)

        // create dog using dog-aware sandbox
        val dog = sandboxOne.createDog("Stray", owner = "Not Known")

        // create persist request for the sandbox that isn't dog-aware
        val request = EntityRequest(
            virtualNodeInfoTwo.holdingIdentity.toAvro(),
            PersistEntities(listOf(sandboxOne.serialize(dog.instance))),
            EXTERNAL_EVENT_CONTEXT
        )
        val processor = EntityMessageProcessor(
            myEntitySandboxService,
            responseFactory,
            this::noOpPayloadCheck
        )

        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, request))

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(records)

        // And check the results

        // It's a failure
        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull
        // The failure is correctly categorised - serialization fails within the database path of the code.
        // It can never succeed on retry, therefore, it's fatal.
        assertThat(response.error.errorType).isEqualTo(ExternalEventResponseErrorType.PLATFORM)
        // The failure also captures the exception name.
        assertThat(response.error.exception.errorType).contains("NotSerializableException")
    }

    @Test
    fun `persist two entities of different types to an actual database in 1 operation`() {
        val dog = sandbox.createDog("Pluto")
        val cat = sandbox.createCat("Larry")

        val responses = assertPersistEntities(dog.instance, cat.instance)
        assertThat(responses.size).isEqualTo(1) // did we get everything we expected?

        val findDog = findDogDirectInDb(dog.id)

        // It's the dog we persisted.
        assertThat(findDog).isEqualTo(dog.instance)
        logger.info("Woof $findDog")

        // use our 'find' code to find the cat, which has a *composite key*
        // (that we also need to create via reflection)
        val catKey = sandbox.createCatKeyInstance(cat.id, "Larry")
        val result = assertFindEntities(CAT_CLASS_NAME, catKey)

        assertThat(result).containsOnly(cat.instance) // It's the cat we persisted.
    }

    @Test
    fun `find in db`() {
        val basilTheDog = sandbox.createDog("Basil")
        persistDirectInDb(basilTheDog.instance)     // write the dog *directly* to the database (don't use 'our' code).
        val result = assertFindEntities(DOG_CLASS_NAME, basilTheDog.id) // use API to find it
        assertThat(result).containsOnly(basilTheDog.instance)
    }

    @Test
    fun `find multiple by ids`() {
        val basilTheDog = sandbox.createDog("Basil", UUID.randomUUID())
        val cloverTheDog = sandbox.createDog("Clover", UUID.randomUUID())
        persistDirectInDb(
            basilTheDog.instance,
            cloverTheDog.instance
        )     // write the dog *directly* to the database (don't use 'our' code).
        val result = assertFindEntities(DOG_CLASS_NAME, basilTheDog.id, cloverTheDog.id) // use API to find it
        assertThat(result).containsOnly(basilTheDog.instance, cloverTheDog.instance)
    }

    @Test
    fun `multiple persist and finds using message processor`() {
        val basilTheDog = sandbox.createDog("Basil")
        val lassieTheDog = sandbox.createDog("Lassie")
        persistDirectInDb(basilTheDog.instance, lassieTheDog.instance)
        val result = assertFindEntities(DOG_CLASS_NAME, basilTheDog.id) // use API to find it
        assertThat(result).containsOnly(basilTheDog.instance)
        val result2 = assertFindEntities(DOG_CLASS_NAME, lassieTheDog.id) // use API to find it
        assertThat(result2).containsOnly(lassieTheDog.instance)
    }

    @Test
    fun `persist zero items`() = assertPersistEntities()

    @Test
    fun `merge in db`() {
        // save a dog
        val dog = sandbox.createDog("Basil")
        persistDirectInDb(dog.instance)

        // change the dog's name, but not changing the ID
        val bellaTheDog = sandbox.createDog("Bella", id = dog.id)

        val results = assertMergeEntities(bellaTheDog.instance)
        assertThat(results).isEqualTo(listOf(bellaTheDog.instance))

        // and can be found in the DB
        val actual = findDogDirectInDb(dog.id)
        assertThat(actual).isEqualTo(bellaTheDog.instance)
    }

    @Test
    fun `merge multiple in db`() {
        // save a dog
        val dog = sandbox.createDog("Basil")
        val dog2 = sandbox.createDog("Lassie")
        persistDirectInDb(dog.instance) // don't write dog2 yet so we test what happens when you merge on both existent and non-existent records

        // change the dog's name twice, without changing the ID
        val bellaTheDog = sandbox.createDog("Bella", id = dog.id)
        val totoTheDog = sandbox.createDog("Toto", id = dog.id)
        val timmyTheDog = sandbox.createDog("Timmy", id = dog2.id)
        val results = assertMergeEntities(bellaTheDog.instance, totoTheDog.instance, timmyTheDog.instance)
        // All the merges will compete at once, then the resulting entities are found, so we get [toto, toto, timmy]
        // rather than [bella, toto, timmy]
        assertThat(results).isEqualTo(listOf(totoTheDog.instance, totoTheDog.instance, timmyTheDog.instance))

        // and can be found in the DB
        val actual = findDogDirectInDb(dog.id)
        assertThat(actual).isEqualTo(totoTheDog.instance)
    }

    @Test
    fun `merge zero items`() = assertMergeEntities()

    @Test
    fun `delete from db`() {
        val dog = sandbox.createDog(
            "Peggy the Pug",
            date = LocalDate.of(2015, 1, 11).atStartOfDay().toInstant(ZoneOffset.UTC),
            owner = "DanTDM"
        )
        persistDirectInDb(dog.instance)

        assertDeleteEntities(dog.instance) // use API to remove it

        val actual = findDogDirectInDb(dog.id)
        assertThat(actual).isNull()
    }

    @Test
    fun `delete multiple from db`() {
        val dogs = createDogs(sandbox)
        dogs.map { persistDirectInDb(it.instance) }
        assertDeleteEntities(dogs[0].instance, dogs[1].instance) // use API to remove first two dogs

        val missing = findDogDirectInDb(dogs[1].id)
        assertThat(missing).isNull()
        val actual = findDogDirectInDb(dogs[2].id)
        assertThat(actual).isEqualTo(dogs[2].instance)
        val r = assertQuery(QuerySetup.NamedQuery(mapOf(), query = "Dog.all"))
        assertThat(r.size).isEqualTo(dogs.size - 2)
    }

    @Test
    fun `delete zero items`() = assertDeleteEntities()

    @Test
    fun `delete by id`() {
        val dog = sandbox.createDog()
        persistDirectInDb(dog.instance)

        assertDeleteEntitiesById(DOG_CLASS_NAME, dog.id)  // use API to remove it

        // Check there's nothing.
        val actual = findDogDirectInDb(dog.id)
        assertThat(actual).isNull()
    }

    @Test
    fun `delete multiple items by id`() {
        val dogs = createDogs(sandbox)
        dogs.map { persistDirectInDb(it.instance) }
        assertDeleteEntitiesById(DOG_CLASS_NAME, dogs[0].id, dogs[2].id, dogs[4].id)
        val r = assertQuery(QuerySetup.NamedQuery(mapOf(), query = "Dog.all"))
        assertThat(r.size).isEqualTo(5)
        assertThat(dogs[0].toString()).contains("Rover")
        r.forEach {
            assertThat(it.toString()).doesNotContain("Rover")
        }
    }

    @Test
    fun `delete zero ids`() = assertDeleteEntitiesById(DOG_CLASS_NAME)

    @Test
    fun `delete with multiple ids`() {
        val dogs = arrayOf("Athos", "Porthos", "Aramis").map {
            val dog = sandbox.createDog(
                it,
                date = LocalDate.of(2015, 1, 11).atStartOfDay().toInstant(ZoneOffset.UTC),
                owner = "Musketeer"
            )
            persistDirectInDb(dog.instance)
            dog
        }
        // use API to remove 2 of the dogs
        assertDeleteEntitiesById(DOG_CLASS_NAME, dogs[0].id, dogs[1].id)

        // Check first dog is gone
        val actual = findDogDirectInDb(dogs[0].id)
        assertThat(actual).isNull()

        // Check third dog is still in database
        val actual2 = findDogDirectInDb(dogs[2].id)
        assertThat(actual2).isNotNull()
    }

    @Test
    fun `delete by id is still successful if id not found`() {
        val dog = sandbox.createDog()
        persistDirectInDb(dog.instance)

        val differentDogId = UUID.randomUUID()
        assertDeleteEntitiesById(DOG_CLASS_NAME, differentDogId)

        // we should not have deleted anything, and also not thrown either, i.e. the response contains a
        // 'success' message.

        // The original dog should still be in the database
        val actual = findDogDirectInDb(dog.id)
        assertThat(actual).isNotNull
    }

    @Test
    fun `find all`() {
        val expected = persistDogs()
        val results = assertQuery(QuerySetup.All(DOG_CLASS_NAME))
        assertThat(results.size).isGreaterThanOrEqualTo(expected)

        // And check the types we've returned
        val dogClass = entitySandboxService.getClass(virtualNodeInfo.holdingIdentity, DOG_CLASS_NAME)
        results.forEach {
            assertThat(it).isInstanceOf(dogClass)
        }
    }


    @Test
    fun `find all with pagination`() {
        val expected = persistDogs()
        val results1 = assertQuery(QuerySetup.All(DOG_CLASS_NAME), 0, 2)
        val results2 = assertQuery(QuerySetup.All(DOG_CLASS_NAME), 2, 2)
        val resultsBalance = assertQuery(QuerySetup.All(DOG_CLASS_NAME), 4, Int.MAX_VALUE)

        assertThat(results1.size).isEqualTo(2)
        assertThat(results2.size).isEqualTo(2)
        assertThat(resultsBalance.size).isEqualTo(expected - 4)

        // And check the types we've returned
        val dogClass = entitySandboxService.getClass(virtualNodeInfo.holdingIdentity, DOG_CLASS_NAME)
        val allResults = results1 + results2 + resultsBalance
        allResults.forEach {
            assertThat(it).isInstanceOf(dogClass)
        }
        assertThat(allResults.map { it.toString() }.toSet().size).isEqualTo(expected) // Check results don't overlap
    }


    @Test
    fun `find all with negative pagination produces error`() {
        persistDogs()
        assertQuery(QuerySetup.All(DOG_CLASS_NAME), -12, 2, expectFailure = "Invalid negative offset -12")
        assertQuery(QuerySetup.All(DOG_CLASS_NAME), 0, -42, expectFailure = "Invalid negative limit -42")
    }

    /**
     * AT THE TIME OF WRITING - if 'find all' returns a set of results and the size
     * of that set of results exceeds a kafka packet size, then we return an error response.
     * The caller may use pagination to workaround this, provided individual result rows fit in
     * Kafka message.
     */
    @Test
    fun `find all exceeds kakfa packet size`() {
        persistDogs()

        val processor = getMessageProcessor {
            if (it.array().size > 50) throw KafkaMessageSizeException("Too large")
            it
        }
        val request = createRequest(virtualNodeInfo.holdingIdentity, FindAll(DOG_CLASS_NAME, 0, Int.MAX_VALUE))

        val responses =
            assertFailureResponses(processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), request))))

        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error.exception.errorType).contains("KafkaMessageSizeException")
    }

    @Test
    fun `find exceeds kakfa packet size`() {
        val dog = sandbox.createDog("K9", owner = "Doctor Who")
        persistDirectInDb(dog.instance)

        val processor = getMessageProcessor {
            if (it.array().size > 4) throw KafkaMessageSizeException("Too large")
            it
        }
        val request = createRequest(
            virtualNodeInfo.holdingIdentity,
            FindEntities(DOG_CLASS_NAME, listOf(sandbox.serialize(dog.id)))
        )

        val responses =
            assertFailureResponses(processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), request))))

        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error.exception.errorType).contains("KafkaMessageSizeException")
    }

    @Test
    fun `merge exceeds kakfa packet size`() {
        val dog = sandbox.createDog("K9", owner = "Doctor Who Tom Baker")
        persistDirectInDb(dog.instance)

        val modifiedDog = sandbox.createDog("K9", owner = "Doctor Who Peter Davidson", id = dog.id)

        val processor = getMessageProcessor {
            if (it.array().size > 4) throw KafkaMessageSizeException("Too large")
            it
        }
        val request = createRequest(
            virtualNodeInfo.holdingIdentity,
            MergeEntities(listOf(sandbox.serialize(modifiedDog.instance)))
        )

        val responses =
            assertFailureResponses(processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), request))))

        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error.exception.errorType).contains("KafkaMessageSizeException")
    }

    /** Cat class has composite key, so also check we find those ok */
    @Test
    fun `find all with composite key`() {
        val expected = persistCats()
        val results = assertQuery(QuerySetup.All(CAT_CLASS_NAME))

        assertThat(results.size).isEqualTo(expected)

        // And check the types we've returned
        val clazz = entitySandboxService.getClass(virtualNodeInfo.holdingIdentity, CAT_CLASS_NAME)
        results.forEach {
            assertThat(it).isInstanceOf(clazz)
        }
    }

    @Test
    fun `persist find and remove with composite key`() {
        val name = "Mr Bigglesworth"
        val cat = sandbox.createCat(name, colour = "hairless", ownerName = "Dr Evil", ownerAge = 40)
        assertPersistEntities(cat.instance)
        val catKey = sandbox.createCatKeyInstance(cat.id, name)

        val actualCat = assertFindEntities(CAT_CLASS_NAME, catKey)

        assertThat(actualCat).containsOnly(cat.instance)
        assertDeleteEntities(cat.instance)

        val result = assertFindEntities(CAT_CLASS_NAME, catKey)
        assertThat(result).isEmpty()
    }

    @Test
    fun `find with named query with many results`() {
        persistDogs()
        val r = assertQuery(QuerySetup.NamedQuery(mapOf("name" to "%o%"), query = "Dog.summonLike"))
        assertThat(r.size).isEqualTo(4)
    }

    @Test
    fun `find with named query with 1 result`() {
        persistDogs()
        val r = assertQuery(QuerySetup.NamedQuery(mapOf("name" to "Rover 1"), query = "Dog.summon"))
        assertThat(r.size).isEqualTo(1)
    }

    @Test
    fun `find with named query and missing owner`() {
        persistDogs()
        val r = assertQuery(QuerySetup.NamedQuery(mapOf(), query = "Dog.independent"))
        assertThat(r.size).isEqualTo(1)
    }

    @Test
    fun `update produces error`() {
        persistDogs()
        assertQuery(
            QuerySetup.NamedQuery(mapOf(), query = "Dog.release"),
            expectFailure = "Not supported for DML operations"
        )
    }

    @Test
    fun `find with named query and incorrectly named parameter`() {
        persistDogs()
        assertQuery(
            QuerySetup.NamedQuery(mapOf("handle" to "Rover 1"), query = "Dog.summon"),
            expectFailure = "Could not locate named parameter [handle], expecting one of [name]"
        )
    }

    @Test
    fun `find with incorrectly named parameter`() {
        persistDogs()
        assertQuery(
            QuerySetup.NamedQuery(mapOf("name" to "Rover 1"), query = "Dog.findByOwner"),
            expectFailure = "No query defined for that name [Dog.findByOwner]"
        )
    }

    @Test
    fun `find with named query with all results`() {
        persistDogs()
        val r = assertQuery(QuerySetup.NamedQuery(mapOf(), query = "Dog.all"))
        assertThat(r.size).isEqualTo(8)
    }

    @Test
    fun `find with named query and zero limit returns no results`() {
        persistDogs()
        val r = assertQuery(QuerySetup.NamedQuery(mapOf(), query = "Dog.all"), limit = 0)
        assertThat(r.size).isEqualTo(0)
    }

    @Test
    fun `find with named query and negative pagination produces error`() {
        persistDogs()
        assertQuery(
            QuerySetup.NamedQuery(mapOf(), query = "Dog.all"),
            -12,
            2,
            expectFailure = "Invalid negative offset -12"
        )
        assertQuery(
            QuerySetup.NamedQuery(mapOf(), query = "Dog.all"),
            0,
            -42,
            expectFailure = "Invalid negative limit -42"
        )
    }

    @Test
    fun `find with named query with pagination`() {
        persistDogs()
        val r = assertQuery(QuerySetup.NamedQuery(mapOf(), query = "Dog.all"), 0, 2)
        assertThat(r.size).isEqualTo(2)
        assertThat(r[0].toString()).contains("Butch 1")
        assertThat(r[1].toString()).contains("Eddie 1")
        val r2 = assertQuery(QuerySetup.NamedQuery(mapOf(), query = "Dog.all"), 2, 2)
        assertThat(r.size).isEqualTo(2)
        assertThat(r2[0].toString()).contains("Gromit 1")
        assertThat(r2[1].toString()).contains("Lassie 1")
    }

    @Test
    fun `find with named query with excessive pagination`() {
        persistDogs()
        val r = assertQuery(QuerySetup.NamedQuery(mapOf(), query = "Dog.all"), 0, 1000)
        assertThat(r.size).isEqualTo(8)
    }

    @Test
    fun `find with named query with 0 results`() {
        persistDogs()
        val r = assertQuery(QuerySetup.NamedQuery(mapOf("name" to "Topcat"), query = "Dog.summon"))
        assertThat(r.size).isEqualTo(0)
    }


    @Test
    fun `find with named query result which hits Kafka message size limit`() {
        persistDogs()
        assertQuery(QuerySetup.NamedQuery(mapOf(), query = "Dog.all"), expectFailure = "Too large", sizeLimit = 10)
    }


    private fun createEntitySandbox(dbConnectionManager: DbConnectionManager = BasicMocks.dbConnectionManager()) =
        EntitySandboxServiceFactory().create(
            virtualNode.sandboxGroupContextComponent,
            cpiInfoReadService,
            virtualNodeInfoReadService,
            dbConnectionManager
        )

    private fun findDogDirectInDb(dogId: UUID): Any? = findDirectInDb(dogId, dogClass)

    private fun findDirectInDb(id: Any, clazz: Class<*>): Any? =
        entityManagerFactory.createEntityManager().use {
            return it.find(clazz, id)
        }

    private fun persistDirectInDb(vararg any: Any) = entityManagerFactory.createEntityManager().transaction { em ->
        any.forEach { en -> em.persist(en) }
    }

    private fun assertSuccessResponses(records: List<Record<*, *>>): List<Record<*, *>> {
        records.forEach {
            val flowEvent = it.value as FlowEvent
            val response = flowEvent.payload as ExternalEventResponse
            if (response.error != null) {
                logger.error("Incorrect error response: ${response.error}")
            }
            assertThat(response.error).isNull()
        }
        return records
    }

    private fun assertFailureResponses(records: List<Record<*, *>>): List<Record<*, *>> {
        records.forEach {
            val flowEvent = it.value as FlowEvent
            val response = flowEvent.payload as ExternalEventResponse
            if (response.error == null) {
                logger.error("Incorrect successful response: ${response.error}")
            }
            assertThat(response.error).isNotNull()
        }
        return records
    }

    private fun createRequest(
        holdingId: net.corda.virtualnode.HoldingIdentity,
        entity: Any,
        externalEventContext: ExternalEventContext = EXTERNAL_EVENT_CONTEXT
    ): EntityRequest {
        logger.info("Entity Request - entity: ${entity.javaClass.simpleName} $entity")
        return EntityRequest(holdingId.toAvro(), entity, externalEventContext)
    }

    private fun assertQuery(
        querySetup: QuerySetup,
        offset: Int = 0, limit: Int = Int.MAX_VALUE,
        expectFailure: String? = null, sizeLimit: Int = Int.MAX_VALUE
    ): List<*> {
        val rec = when (querySetup) {
            is QuerySetup.NamedQuery -> {
                val paramsSerialized = querySetup.params.mapValues { v -> sandbox.serialize(v.value) }
                FindWithNamedQuery(querySetup.query, paramsSerialized, offset, limit)
            }
            is QuerySetup.All -> {
                FindAll(querySetup.className, offset, limit)
            }
        }
        val processor = getMessageProcessor {
            val size = it.array().size
            logger.info("payload check size $size c/w limit $sizeLimit")
            if (size > sizeLimit) throw KafkaMessageSizeException("Too large; size $size exceeds limit $sizeLimit")
            it
        }
        val request = createRequest(virtualNodeInfo.holdingIdentity, rec)
        val records = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), request)))
        assertThat(records.size).withFailMessage("can only use this helper method with 1 result").isEqualTo(1)
        val record = records.first()
        val flowEvent = record.value as FlowEvent
        if (expectFailure != null) {
            val response = flowEvent.payload as ExternalEventResponse
            if (response.error != null) {
                logger.error("Error response: ${response.error} (expected failure)")
                assertThat(response.error).isNotNull()

            }
            assertThat(response.error.toString()).contains(expectFailure)
            return listOf<String>()
        } else {
            val entityResponse = deserializer.deserialize(
                (flowEvent.payload as ExternalEventResponse).payload.array()
            )!!
            return entityResponse.results.map { sandbox.deserialize(it) }
        }
    }

    /** Delete entity and assert
     * @return the list of successful responses
     * */
    private fun assertDeleteEntities(vararg objs: Any): Record<*, *> {
        val processor = getMessageProcessor(this::noOpPayloadCheck)

        val responses = assertSuccessResponses(
            processor.onNext(
                listOf(
                    Record(
                        TOPIC,
                        UUID.randomUUID().toString(),
                        createRequest(
                            virtualNodeInfo.holdingIdentity,
                            DeleteEntities(objs.map { sandbox.serialize(it) })
                        )
                    )
                )
            )
        )
        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNull()
        return responses.first()
    }

    /** Delete entity by primary key and do some asserting
     * @return the list of successful responses
     * */
    private fun assertDeleteEntitiesById(className: String, vararg objs: UUID): List<Record<*, *>> {
        val deleteByPrimaryKey = DeleteEntitiesById(className, objs.map { sandbox.serialize(it) })
        val processor = getMessageProcessor(this::noOpPayloadCheck)
        val records = listOf(
            Record(
                TOPIC,
                UUID.randomUUID().toString(),
                createRequest(virtualNodeInfo.holdingIdentity, deleteByPrimaryKey)
            )
        )
        return processor.onNext(records)
    }

    /** Find an entity and do some asserting
     * @return the list of successful responses
     * */
    private fun assertFindEntities(className: String, vararg obj: Any): List<*> {
        val processor = getMessageProcessor(this::noOpPayloadCheck)

        val responses = assertSuccessResponses(
            processor.onNext(
                listOf(
                    Record(
                        TOPIC,
                        UUID.randomUUID().toString(),
                        createRequest(
                            virtualNodeInfo.holdingIdentity,
                            FindEntities(className, obj.map { sandbox.serialize(it) })
                        )
                    )
                )
            )
        )

        val flowEvent = responses.first().value as FlowEvent
        val response = deserializer.deserialize((flowEvent.payload as ExternalEventResponse).payload.array())!!

        return response.results.map { sandbox.deserialize(it) }
    }

    /** Persist an entity and do some asserting
     * @return the list of successful responses
     */
    private fun assertPersistEntities(vararg entities: Any): List<Record<*, *>> {
        val processor = getMessageProcessor(this::noOpPayloadCheck)

        val requestId = UUID.randomUUID().toString()
        val responses = assertSuccessResponses(
            processor.onNext(
                listOf(
                    Record(
                        TOPIC,
                        requestId,
                        createRequest(
                            virtualNodeInfo.holdingIdentity,
                            PersistEntities(entities.map { sandbox.serialize(it) }),
                            ExternalEventContext(requestId, "flow id", KeyValuePairList(emptyList()))
                        )
                    )
                )
            )
        )
        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.requestId).isEqualTo(requestId)
        assertThat(response.error).isNull()
        return responses
    }

    /** Merge an entity and do some asserting
     * @return the list of successful responses
     */
    private fun assertMergeEntities(vararg objs: Any): List<Any> {
        val processor = getMessageProcessor(this::noOpPayloadCheck)

        val responses = assertSuccessResponses(
            processor.onNext(
                listOf(
                    Record(
                        TOPIC,
                        UUID.randomUUID().toString(),
                        createRequest(
                            virtualNodeInfo.holdingIdentity,
                            MergeEntities(objs.map { sandbox.serialize(it) })
                        )
                    )
                )
            )
        )
        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNull()
        val entityResponse = deserializer.deserialize(response.payload.array())!!
        val bytes = entityResponse.results as List<ByteBuffer>
        return bytes.map { sandbox.deserialize(it) }
    }

    /** Persists some dogs DIRECTLY to the database, bypassing the code under test, so that we can then interact
     * with the dog entities in the database */
    private fun persistDogs(times: Int = 1): Int {
        val dogs = createDogs(sandbox, times)
        dogs.map { persistDirectInDb(it.instance) }
        return dogs.size
    }

    /** Persists some cats DIRECTLY to the database */
    private fun persistCats(times: Int = 1): Int {
        val cats = createCats(sandbox, times)
        cats.map { persistDirectInDb(it.instance) }
        return cats.size
    }

    private fun SandboxGroupContext.serialize(obj: Any) = ByteBuffer.wrap(getSerializationService().serialize(obj).bytes)

    /** Simple wrapper to deserialize */
    private fun SandboxGroupContext.deserialize(bytes: ByteBuffer) =
        getSerializationService().deserialize(bytes.array(), Any::class.java)

    private fun getMessageProcessor(payloadCheck: (bytes: ByteBuffer) -> ByteBuffer): EntityMessageProcessor {
        return EntityMessageProcessor(
            entitySandboxService,
            responseFactory,
            payloadCheck
        )
    }
}
