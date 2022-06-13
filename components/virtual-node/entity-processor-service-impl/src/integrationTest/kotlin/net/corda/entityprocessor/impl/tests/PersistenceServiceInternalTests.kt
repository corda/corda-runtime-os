package net.corda.entityprocessor.impl.tests

import java.nio.ByteBuffer
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.UUID
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.FlowKey
import net.corda.data.persistence.DeleteEntity
import net.corda.data.persistence.DeleteEntityById
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseFailure
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.data.persistence.Error
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntity
import net.corda.data.persistence.MergeEntity
import net.corda.data.persistence.PersistEntity
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.entityprocessor.impl.internal.EntitySandboxServiceImpl
import net.corda.entityprocessor.impl.internal.PersistenceServiceInternal
import net.corda.entityprocessor.impl.internal.exceptions.KafkaMessageSizeException
import net.corda.entityprocessor.impl.internal.getClass
import net.corda.entityprocessor.impl.tests.components.VirtualNodeService
import net.corda.entityprocessor.impl.tests.fake.FakeDbConnectionManager
import net.corda.entityprocessor.impl.tests.helpers.AnimalCreator.persistCats
import net.corda.entityprocessor.impl.tests.helpers.AnimalCreator.persistDogs
import net.corda.entityprocessor.impl.tests.helpers.BasicMocks
import net.corda.entityprocessor.impl.tests.helpers.DbTestContext
import net.corda.entityprocessor.impl.tests.helpers.Resources
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.CAT_CLASS_NAME
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.DOG_CLASS_NAME
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createCatInstance
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createCatKeyInstance
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createDogInstance
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getCatClass
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getDogClass
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getOwnerClass
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getSerializer
import net.corda.messaging.api.records.Record
import net.corda.orm.JpaEntitiesSet
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
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
    companion object {
        const val TOPIC = "pretend-topic"
        private val logger = contextLogger()
    }

    @InjectService
    lateinit var lbm: LiquibaseSchemaMigrator

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService

    private lateinit var ctx: DbTestContext

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
        }
    }

    @BeforeEach
    private fun beforeEach() {
        ctx = createDbTestContext()
        // Each test is likely to leave junk lying around in the tables before the next test.
        // We can't trust deleting the tables because tests can run concurrently.
    }

    /** Simple wrapper to serialize bytes correctly during test */
    private fun SandboxGroupContext.serialize(obj: Any) = ByteBuffer.wrap(getSerializer().serialize(obj).bytes)

    /** Simple wrapper to serialize bytes correctly during test */
    private fun DbTestContext.serialize(obj: Any) = sandbox.serialize(obj)

    /** Simple wrapper to deserialize */
    private fun SandboxGroupContext.deserialize(bytes: ByteBuffer) =
        getSerializer().deserialize(bytes.array(), Any::class.java)

    /** Simple wrapper to deserialize */
    private fun DbTestContext.deserialize(bytes: ByteBuffer) = sandbox.deserialize(bytes)

    private fun noOpPayloadCheck(bytes: ByteBuffer) = bytes

    @Test
    fun `persist`() {
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)

        val entitySandboxService =
            EntitySandboxServiceImpl(
                virtualNode.sandboxGroupContextComponent,
                cpiInfoReadService,
                virtualNodeInfoReadService,
                BasicMocks.dbConnectionManager(),
                BasicMocks.componentContext()
            )

        val sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity)

        val dogId = UUID.randomUUID()
        logger.info("Persisting $dogId/rover")

        val requestId = UUID.randomUUID().toString()

        val persistenceService =
            PersistenceServiceInternal(entitySandboxService::getClass, requestId, UTCClock(), this::noOpPayloadCheck)
        val dog = sandbox.createDogInstance(dogId, "Rover", Instant.now(), "me")
        val payload = PersistEntity(sandbox.serialize(dog))

        val entityManager = BasicMocks.entityManager()

        persistenceService.persist(sandbox.getSerializer(), entityManager, payload)

        Mockito.verify(entityManager).persist(Mockito.any())
    }

    @Test
    fun `persist via message processor`() {
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)
        val entitySandboxService =
            EntitySandboxServiceImpl(
                virtualNode.sandboxGroupContextComponent,
                cpiInfoReadService,
                virtualNodeInfoReadService,
                BasicMocks.dbConnectionManager(),
                BasicMocks.componentContext()
            )

        val sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity)

        val dog = sandbox.createDogInstance(UUID.randomUUID(), "Walter", Instant.now(), "me")
        val request = createRequest(virtualNodeInfo.holdingIdentity, PersistEntity(sandbox.serialize(dog)))
        val processor = EntityMessageProcessor(entitySandboxService, UTCClock(), this::noOpPayloadCheck)

        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, request))
        val responses = processor.onNext(records)

        assertThat(responses.size).isEqualTo(1)
        val response = responses.first().value as EntityResponse
        assertThat(response.requestId).isEqualTo(requestId)
    }

    @Test
    fun `persist using two different sandboxes captures exception in response`() {
        val virtualNodeInfoOne = virtualNode.load(Resources.EXTENDABLE_CPB)
        val virtualNodeInfoTwo = virtualNode.load(Resources.CALCULATOR_CPB)

        val animalDbConnection = Pair(virtualNodeInfoOne.vaultDmlConnectionId, "animals-node")
        val calcDbConnection = Pair(virtualNodeInfoTwo.vaultDmlConnectionId, "calc-node")
        val dbConnectionManager = FakeDbConnectionManager(listOf(animalDbConnection, calcDbConnection))

        val entitySandboxService =
            EntitySandboxServiceImpl(
                virtualNode.sandboxGroupContextComponent,
                cpiInfoReadService,
                virtualNodeInfoReadService,
                dbConnectionManager,
                BasicMocks.componentContext()
            )

        val sandboxOne = entitySandboxService.get(virtualNodeInfoOne.holdingIdentity)

        // create dog using dog-aware sandbox
        val dog = sandboxOne.createDogInstance(UUID.randomUUID(), "Stray", Instant.now(), "Not Known")

        // create persist request for the sandbox that isn't dog-aware
        val flowKey = FlowKey(UUID.randomUUID().toString(), virtualNodeInfoTwo.holdingIdentity.toAvro())
        val request = EntityRequest(Instant.now(), UUID.randomUUID().toString(), flowKey, PersistEntity(sandboxOne.serialize(dog)))
        val processor = EntityMessageProcessor(entitySandboxService, UTCClock(), this::noOpPayloadCheck)
        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, request))

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(records)

        // And check the results

        // It's a failure
        assertThat(responses.size).isEqualTo(1)
        assertThat((responses[0].value as EntityResponse).responseType).isInstanceOf(EntityResponseFailure::class.java)

        val responseFailure = (responses[0].value as EntityResponse).responseType as EntityResponseFailure

        // The failure is correctly categorised - serialization fails within the database path of the code.
        // It can never succeed on retry, therefore, it's fatal.
        assertThat(responseFailure.errorType).isEqualTo(Error.FATAL)

        // The failure also captures the exception name.
        assertThat(responseFailure.exception.errorType).contains("NotSerializableException")
    }

    @Test
    fun `persist to an actual database`() {
        // request persist - cats & dogs are in different CPKs/bundles

        // Firstly, create a 'dog'.  Note that we've used reflection (if you click through) to construct the dog
        // object so that we're using the cpks that we have *loaded* into this process from the resources.
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(
            dogId,
            "Pluto",
            // Truncating to millis as nanos get lost in Windows JDBC driver.
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            "me"
        )
        val dogRequest = createRequest(ctx.virtualNodeInfo.holdingIdentity, PersistEntity(ctx.serialize(dog)))

        // Now create a cat instance in the same way.
        val catId = UUID.randomUUID()
        val catName = "Garfield"
        val ownerId = UUID.randomUUID()
        val cat = ctx.sandbox.createCatInstance(
            catId,
            catName,
            "ginger",
            ownerId,
            "Jim Davies",
            Calendar.getInstance().get(Calendar.YEAR) - 1976
        )
        val catRequest = EntityRequest(
            dogRequest.timestamp,
            dogRequest.flowId,
            dogRequest.flowKey,
            PersistEntity(ctx.serialize(cat))
        )

        // Now send the two messages (both 'persist') to the message processor.  This is the point where we would
        // 'receive them' from the flow-worker via Kafka
        val processor = EntityMessageProcessor(ctx.entitySandboxService, UTCClock(), this::noOpPayloadCheck)
        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, dogRequest), Record(TOPIC, requestId, catRequest))

        // Process the messages (and assert them).  This will persist the cat+dog to the db.
        val responses = assertSuccessResponses(processor.onNext(records))

        // assert persisted
        assertThat(responses.size).isEqualTo(2)

        // check the db directly (rather than using our code)
        val findDog = ctx.findDog(dogId)

        // It's the dog we persisted.
        assertThat(findDog).isEqualTo(dog)
        logger.info("Woof $findDog")

        // use our 'find' code to find the cat, which has a *composite key*
        // (that we also need to create via reflection)
        val catKey = ctx.sandbox.createCatKeyInstance(catId, catName)
        val bytes = assertFindEntity(CAT_CLASS_NAME, ctx.serialize(catKey))

        // It's the cat we persisted.
        assertThat(ctx.deserialize(bytes!!)).isEqualTo(cat)
    }

    @Test
    fun `find in db`() {
        // save a dog
        val basilId = UUID.randomUUID()
        val basilTheDog = ctx.sandbox.createDogInstance(
            basilId,
            "Basil",
            // Truncating to millis as nanos get lost in Windows JDBC driver.
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            "me"
        )

        // write the dog *directly* to the database (don't use 'our' code).
        ctx.persist(basilTheDog)

        // use API to find it
        val bytes = assertFindEntity(DOG_CLASS_NAME, ctx.serialize(basilId))

        // assert it's the dog
        val result = ctx.deserialize(bytes!!)
        assertThat(result).isEqualTo(basilTheDog)
    }

    @Test
    fun `merge in db`() {
        // save a dog
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(dogId, "Basil", Instant.now(), "me")
        ctx.persist(dog)

        // change the dog's name
        val bellaTheDog = ctx.sandbox.createDogInstance(
            dogId,
            "Bella",
            // Truncating to millis as nanos get lost in Windows JDBC driver.
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            "me"
        )

        // use API to find it
        val responses = assertMergeEntity(ctx.serialize(bellaTheDog))

        // assert the change
        assertThat(responses.size).isEqualTo(1)

        // assert that Bella has been returned

        val entityResponse = responses[0].value as EntityResponse
        assertThat(entityResponse.responseType as EntityResponseSuccess).isInstanceOf(EntityResponseSuccess::class.java)
        val entityResponseSuccess = entityResponse.responseType as EntityResponseSuccess
        val bytes = entityResponseSuccess.result as ByteBuffer
        val responseEntity = ctx.deserialize(bytes)
        assertThat(responseEntity).isEqualTo(bellaTheDog)

        // and can be found in the DB
        val actual = ctx.findDog(dogId)
        assertThat(actual).isEqualTo(bellaTheDog)
    }

    @Test
    fun `remove from db`() {
        // save a dog
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(
            dogId,
            "Peggy the Pug",
            LocalDate.of(2015, 1, 11).atStartOfDay().toInstant(ZoneOffset.UTC),
            "DanTDM"
        )
        ctx.persist(dog)

        // use API to remove it
        val responses = assertDeleteEntity(ctx.serialize(dog))

        // assert the change
        assertThat(responses.size).isEqualTo(1)

        val actual = ctx.findDog(dogId)
        assertThat(actual).isNull()
    }

    @Test
    fun `delete by id`() {
        // save a dog
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(
            dogId,
            "K9",
            LocalDate.of(2015, 1, 11).atStartOfDay().toInstant(ZoneOffset.UTC),
            "Doctor Who"
        )
        ctx.persist(dog)

        // use API to remove it
        val responses = assertDeleteEntityById(DOG_CLASS_NAME, ctx.serialize(dogId))

        // assert the change - one response message (which contains success)
        assertThat(responses.size).isEqualTo(1)

        // Check there's nothing.
        val actual = ctx.findDog(dogId)
        assertThat(actual).isNull()
    }

    @Test
    fun `delete by id is still successful if id not found`() {
        val dogId = UUID.randomUUID()
        ctx.persist(ctx.sandbox.createDogInstance(dogId, "K9", Instant.now(), "Doctor Who"))

        val differentDogId = UUID.randomUUID()
        val responses = assertDeleteEntityById(DOG_CLASS_NAME, ctx.serialize(differentDogId))

        // we should not have deleted anything, and also not thrown either, i.e. the response contains a
        // 'success' message.

        assertThat(responses.size).isEqualTo(1)

        val actual = ctx.findDog(dogId)
        assertThat(actual).isNotNull
    }

    @Test
    fun `find all`() {
        val expected = persistDogs(ctx, 1)

        val results = assertFindAll(DOG_CLASS_NAME)

        assertThat(results.size).isGreaterThanOrEqualTo(expected)

        // And check the types we've returned
        val dogClass = ctx.entitySandboxService.getClass(ctx.virtualNodeInfo.holdingIdentity, DOG_CLASS_NAME)
        results.forEach {
            assertThat(it).isInstanceOf(dogClass)
        }
    }

    /**
     * AT THE TIME OF WRITING - if 'find all' returns a set of results and the size
     * of that set of results exceeds a kafka packet size, then we return an error response.
     */
    @Test
    fun `find all exceeds kakfa packet size`() {
        persistDogs(ctx, 10)

        val processor = EntityMessageProcessor(ctx.entitySandboxService, UTCClock()) {
            if (it.array().size > 50) throw KafkaMessageSizeException("Too large")
            it
        }
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, FindAll(DOG_CLASS_NAME))

        val responses = assertFailureResponses(processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), request))))

        val response = responses.first().value as EntityResponse
        val failure = response.responseType as EntityResponseFailure
        assertThat(failure.exception.errorType).contains("KafkaMessageSizeException")
    }
    @Test
    fun `find exceeds kakfa packet size`() {
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(dogId, "K9", Instant.now(), "Doctor Who")
        ctx.persist(dog)

        val processor = EntityMessageProcessor(ctx.entitySandboxService, UTCClock()) {
            if (it.array().size > 4) throw KafkaMessageSizeException("Too large")
            it
        }
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, FindEntity(DOG_CLASS_NAME, ctx.serialize(dogId)))

        val responses = assertFailureResponses(processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), request))))

        val response = responses.first().value as EntityResponse
        val failure = response.responseType as EntityResponseFailure
        assertThat(failure.exception.errorType).contains("KafkaMessageSizeException")
    }

    @Test
    fun `merge exceeds kakfa packet size`() {
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(dogId, "K9", Instant.now(), "Doctor Who Tom Baker")
        ctx.persist(dog)

        val modifiedDog = ctx.sandbox.createDogInstance(
            dogId,
            "K9",
            // Truncating to millis as nanos get lost in Windows JDBC driver.
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            "Doctor Who Peter Davidson"
        )

        val processor = EntityMessageProcessor(ctx.entitySandboxService, UTCClock()) {
            if (it.array().size > 4) throw KafkaMessageSizeException("Too large")
            it
        }
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, MergeEntity(ctx.serialize(modifiedDog)))

        val responses = assertFailureResponses(processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), request))))

        val response = responses.first().value as EntityResponse
        val failure = response.responseType as EntityResponseFailure
        assertThat(failure.exception.errorType).contains("KafkaMessageSizeException")
    }

    /** Cat class has composite key, so also check we find those ok */
    @Test
    fun `find all with composite key`() {
        val expected = persistCats(ctx, 1)
        val results = assertFindAll(CAT_CLASS_NAME)

        // Of the expected size - if it fails here - check we're cleaning up elsewhere.
        assertThat(results.size).isGreaterThanOrEqualTo(expected)

        // And check the types we've returned
        val clazz = ctx.entitySandboxService.getClass(ctx.virtualNodeInfo.holdingIdentity, CAT_CLASS_NAME)
        results.forEach {
            assertThat(it).isInstanceOf(clazz)
        }
    }


    @Test
    fun `persist find and remove with composite key`() {
        val id = UUID.randomUUID()
        val name = "Mr Bigglesworth"
        val catKey = ctx.sandbox.createCatKeyInstance(id, name)

        val cat = ctx.sandbox.createCatInstance(
            id,
            name,
            "hairless",
            UUID.randomUUID(),
            "Dr Evil",
            40
        )

        assertPersistEntity(ctx.serialize(cat))

        val bytes = assertFindEntity(CAT_CLASS_NAME, ctx.serialize(catKey))
        val actualCat = ctx.deserialize(bytes!!)

        assertThat(cat).isEqualTo(actualCat)

        assertDeleteEntity(ctx.serialize(cat))

        val newBytes = assertFindEntity(CAT_CLASS_NAME, ctx.serialize(catKey))
        assertThat(newBytes).isNull()
    }

    private fun createDbTestContext(): DbTestContext {
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)

        val animalDbConnection = Pair(virtualNodeInfo.vaultDmlConnectionId, "animals-node")
        val dbConnectionManager = FakeDbConnectionManager(listOf(animalDbConnection))

        // set up sandbox
        val entitySandboxService =
            EntitySandboxServiceImpl(
                virtualNode.sandboxGroupContextComponent,
                cpiInfoReadService,
                virtualNodeInfoReadService,
                dbConnectionManager,
                BasicMocks.componentContext()
            )

        val sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity)

        // migrate DB schema
        val dogClass = sandbox.sandboxGroup.getDogClass()
        val catClass = sandbox.sandboxGroup.getCatClass()
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

        return DbTestContext(
            virtualNodeInfo,
            entitySandboxService,
            sandbox,
            dbConnectionManager.createEntityManagerFactory(
                animalDbConnection.first,
                JpaEntitiesSet.create(
                    animalDbConnection.second,
                    setOf(dogClass, catClass, sandbox.sandboxGroup.getOwnerClass())
                )
            ),
            dogClass, catClass
        )
    }

    private fun assertSuccessResponses(records: List<Record<*, *>>): List<Record<*, *>> {
        records.forEach {
            val response = it.value as EntityResponse
            if (response.responseType is EntityResponseFailure) {
                logger.error("$response.responseType")
            }
            assertThat(response.responseType).isInstanceOf(EntityResponseSuccess::class.java)
        }
        return records
    }

    private fun assertFailureResponses(records: List<Record<*, *>>): List<Record<*, *>> {
        records.forEach {
            val response = it.value as EntityResponse
            if (response.responseType is EntityResponseSuccess) {
                logger.error("$response.responseType")
            }
            assertThat(response.responseType).isInstanceOf(EntityResponseFailure::class.java)
        }
        return records
    }

    private fun assertThatResponseIsAList(entityResponse: EntityResponse): List<*> {
        val entityResponseSuccess = entityResponse.responseType as EntityResponseSuccess
        val bytes = entityResponseSuccess.result as ByteBuffer
        val results = ctx.deserialize(bytes)

        // We have a list
        assertThat(results as List<*>).isInstanceOf(List::class.java)

        return results
    }

    private fun createRequest(holdingId: net.corda.virtualnode.HoldingIdentity, entity: Any): EntityRequest {
        val flowKey = FlowKey(UUID.randomUUID().toString(), holdingId.toAvro())
        logger.info("Entity Request - flow: $flowKey, entity: ${entity.javaClass.simpleName} $entity")
        return EntityRequest(Instant.now(), UUID.randomUUID().toString(), flowKey, entity)
    }

    /** Find all for class name and assert
     * @return the list of results (NOT the list of record/responses)
     * */
    private fun assertFindAll(className: String): List<*> {
        val processor = EntityMessageProcessor(ctx.entitySandboxService, UTCClock(), this::noOpPayloadCheck)
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, FindAll(className))

        val responses =
            assertSuccessResponses(processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), request))))

        assertThat(responses.size).withFailMessage("can only use this helper method with 1 result").isEqualTo(1)
        val entityResponse = responses.first().value as EntityResponse

        return assertThatResponseIsAList(entityResponse)
    }

    /** Delete entity and assert
     * @return the list of successful responses
     * */
    private fun assertDeleteEntity(bytes: ByteBuffer): List<Record<*, *>> {
        val processor = EntityMessageProcessor(ctx.entitySandboxService, UTCClock(), this::noOpPayloadCheck)

        return assertSuccessResponses(
            processor.onNext(
                listOf(
                    Record(
                        TOPIC,
                        UUID.randomUUID().toString(),
                        createRequest(ctx.virtualNodeInfo.holdingIdentity, DeleteEntity(bytes))
                    )
                )
            )
        )
    }

    /** Delete entity by primary key and do some asserting
     * @return the list of successful responses
     * */
    private fun assertDeleteEntityById(className: String, bytes: ByteBuffer): List<Record<*, *>> {
        val deleteByPrimaryKey = DeleteEntityById(className, bytes)
        val processor = EntityMessageProcessor(ctx.entitySandboxService, UTCClock(), this::noOpPayloadCheck)
        val records = listOf(
            Record(
                TOPIC,
                UUID.randomUUID().toString(),
                createRequest(ctx.virtualNodeInfo.holdingIdentity, deleteByPrimaryKey)
            )
        )
        return processor.onNext(records)
    }

    /** Find an entity and do some asserting
     * @return the list of successful responses
     * */
    private fun assertFindEntity(className: String, bytes: ByteBuffer): ByteBuffer? {
        val processor = EntityMessageProcessor(ctx.entitySandboxService, UTCClock(), this::noOpPayloadCheck)

        val responses = assertSuccessResponses(
            processor.onNext(
                listOf(
                    Record(
                        TOPIC,
                        UUID.randomUUID().toString(),
                        createRequest(
                            ctx.virtualNodeInfo.holdingIdentity,
                            FindEntity(className, bytes)
                        )
                    )
                )
            )
        )

        assertThat(responses.first().value as EntityResponse).isInstanceOf(EntityResponse::class.java)

        val response = responses.first().value as EntityResponse
        val success = response.responseType as EntityResponseSuccess

        return success.result
    }

    /** Persist an entity and do some asserting
     * @return the list of successful responses
     */
    private fun assertPersistEntity(bytes: ByteBuffer): List<Record<*, *>> {
        val processor = EntityMessageProcessor(ctx.entitySandboxService, UTCClock(), this::noOpPayloadCheck)

        return assertSuccessResponses(
            processor.onNext(
                listOf(
                    Record(
                        TOPIC,
                        UUID.randomUUID().toString(),
                        createRequest(ctx.virtualNodeInfo.holdingIdentity, PersistEntity(bytes))
                    )
                )
            )
        )
    }

    /** Merge an entity and do some asserting
     * @return the list of successful responses
     */
    private fun assertMergeEntity(bytes: ByteBuffer): List<Record<*, *>> {
        val processor = EntityMessageProcessor(ctx.entitySandboxService, UTCClock(), this::noOpPayloadCheck)
        return assertSuccessResponses(
            processor.onNext(
                listOf(
                    Record(
                        TOPIC,
                        UUID.randomUUID().toString(),
                        createRequest(ctx.virtualNodeInfo.holdingIdentity, MergeEntity(bytes))
                    )
                )
            )
        )
    }
}
