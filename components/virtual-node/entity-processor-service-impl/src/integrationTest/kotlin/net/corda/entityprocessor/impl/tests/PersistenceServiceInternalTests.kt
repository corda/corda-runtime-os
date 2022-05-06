package net.corda.entityprocessor.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.virtualnode.DeleteEntity
import net.corda.data.virtualnode.EntityRequest
import net.corda.data.virtualnode.EntityResponse
import net.corda.data.virtualnode.FindEntity
import net.corda.data.virtualnode.MergeEntity
import net.corda.data.virtualnode.PersistEntity
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.entityprocessor.impl.internal.EntitySandboxServiceImpl
import net.corda.entityprocessor.impl.internal.PersistenceServiceInternal
import net.corda.entityprocessor.impl.tests.components.VirtualNodeService
import net.corda.entityprocessor.impl.tests.fake.FakeDbConnectionManager
import net.corda.entityprocessor.impl.tests.helpers.BasicMocks
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.DOG_CLASS_NAME
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createCatInstance
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createDogInstance
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getCatClass
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getDogClass
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getOwnerClass
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getSerializer
import net.corda.messaging.api.records.Record
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.base.util.contextLogger
import net.corda.v5.serialization.SerializedBytes
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
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
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar
import java.util.UUID
import javax.persistence.EntityManagerFactory


/**
 * To use Postgres rather than in-memory (HSQL):
 *
 *     docker run --rm --name test-instance -e POSTGRES_PASSWORD=password -p 5432:5432 postgres
 *
 *     gradlew integrationTest -PpostgresPort=5432
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
            virtualNode = setup.fetchService(timeout = 5000)
            cpiInfoReadService = setup.fetchService(timeout = 5000)
            virtualNodeInfoReadService = setup.fetchService(timeout = 5000)
        }
    }

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

        val persistenceService = PersistenceServiceInternal(entitySandboxService)
        val dog = sandbox.createDogInstance(dogId, "Rover", Instant.now(), "me")
        val payload = PersistEntity(
            ByteBuffer.wrap(sandbox.getSerializer().serialize(dog).bytes)
        )

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
        val request = createRequest(virtualNodeInfo.holdingIdentity, dog)
        val processor = EntityMessageProcessor(entitySandboxService)

        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, request))
        val responses = processor.onNext(records)

        assertThat(responses.size).isEqualTo(1)
        val response = processor.onNext(records).first().value as EntityResponse
        assertThat(response.requestId).isEqualTo(requestId)
    }

    @Test
    fun `persist using two different sandboxes`() {
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
        val serialisedDog = sandboxOne.getSerializer().serialize(dog).bytes

        // create persist request for the sandbox that isn't dog-aware
        val flowKey = FlowKey(UUID.randomUUID().toString(), virtualNodeInfoTwo.holdingIdentity.toAvro())
        val request = EntityRequest(Instant.now(), flowKey, PersistEntity(ByteBuffer.wrap(serialisedDog)))

        val processor = EntityMessageProcessor(entitySandboxService)
        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, request))

        val responses = processor.onNext(records)

        assertThat(responses.size).isEqualTo(1)
        assertThat((responses[0].value as EntityResponse).result).isInstanceOf(ExceptionEnvelope::class.java)
        // TODO - error types should not be string but categories of errors
        assertThat(((responses[0].value as EntityResponse).result as ExceptionEnvelope).errorType).contains("NotSerializableException")
    }

    @Test
    fun `persist to an actual database`() {
        val ctx = createDbTestContext()

        // request persist - cats & dogs are in different CPKs/bundles
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(dogId, "Pluto", Instant.now(), "me")
        val dogRequest = createRequest(
            ctx.virtualNodeInfo.holdingIdentity, PersistEntity(ByteBuffer.wrap(ctx.sandbox.getSerializer().serialize(dog).bytes)))
        val catId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val cat = ctx.sandbox.createCatInstance(
            catId,
            "Garfield",
            "ginger",
            ownerId,
            "Jim Davies",
            Calendar.getInstance().get(Calendar.YEAR) - 1976
        )
        val catRequest = EntityRequest(
            dogRequest.timestamp,
            dogRequest.flowKey,
            PersistEntity(ByteBuffer.wrap(ctx.sandbox.getSerializer().serialize(cat).bytes))
        )

        val processor = EntityMessageProcessor(ctx.entitySandboxService)
        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, dogRequest), Record(TOPIC, requestId, catRequest))
        val responses = processor.onNext(records)

        // assert persisted
        assertThat(responses.size).isEqualTo(2)


        val findDog = ctx.findDog(dogId)
        assertThat(findDog).isEqualTo(dog)
        logger.info("Woof $findDog")

        val findCat = ctx.findCat(catId)
        assertThat(findCat).isEqualTo(cat)
        logger.info("Miaow $findCat")
    }

    @Test
    fun `find in db`() {
        val ctx = createDbTestContext()

        // save a dog
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(dogId, "Basil", Instant.now(), "me")
        ctx.persist(dog)

        // use API to find it
        val serializedDogId = ctx.sandbox.getSerializer().serialize(dogId)
        val findEntity = FindEntity(DOG_CLASS_NAME, ByteBuffer.wrap(serializedDogId.bytes))
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, findEntity)
        val processor = EntityMessageProcessor(ctx.entitySandboxService)
        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, request))
        val responses = processor.onNext(records)

        // assert it's the dog
        assertThat(responses.size).isEqualTo(1)
        val bytes = (responses[0].value as EntityResponse).result as ByteBuffer
        val result = ctx.sandbox.getSerializer().deserialize(bytes.array(), Any::class.java)
        assertThat(result).isEqualTo(dog)
    }

    @Test
    fun `merge in db`() {
        val ctx = createDbTestContext()

        // save a dog
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(dogId, "Basil", Instant.now(), "me")
        ctx.persist(dog)

        // change the dog's name
        val bellaTheDog = ctx.sandbox.createDogInstance(dogId, "Bella", Instant.now(), "me")

        // use API to find it
        val mergeEntity = MergeEntity(ByteBuffer.wrap(ctx.sandbox.getSerializer().serialize(bellaTheDog).bytes))
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, mergeEntity)
        val processor = EntityMessageProcessor(ctx.entitySandboxService)
        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, request))
        val responses = processor.onNext(records)

        // assert the change
        assertThat(responses.size).isEqualTo(1)

        // assert that Bella has been returned
        val bytes = (responses[0].value as EntityResponse).result as ByteBuffer
        val responseEntity = ctx.sandbox.getSerializer().deserialize(bytes.array(), Any::class.java)
        assertThat(responseEntity).isEqualTo(bellaTheDog)

        // and can be found in the DB
        val actual = ctx.findDog(dogId)
        assertThat(actual).isEqualTo(bellaTheDog)
    }

    @Test
    fun `remove from db`() {
        val ctx = createDbTestContext()

        // save a dog
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(
            dogId,
            "Peggy the Pug",
            LocalDate.of(2015, 1, 11).atStartOfDay().toInstant(ZoneOffset.UTC),
            "DanTDM")
        ctx.persist(dog)

        // use API to remove it
        val mergeEntity = DeleteEntity(ByteBuffer.wrap(ctx.sandbox.getSerializer().serialize(dog).bytes))
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, mergeEntity)
        val processor = EntityMessageProcessor(ctx.entitySandboxService)
        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, request))
        val responses = processor.onNext(records)

        // assert the change
        assertThat(responses.size).isEqualTo(1)

        val actual = ctx.findDog(dogId)
        assertThat(actual).isNull()
    }

    private data class DbTestContext(
        val virtualNodeInfo: VirtualNodeInfo,
        val entitySandboxService: EntitySandboxServiceImpl,
        val sandbox: SandboxGroupContext,
        private val entityManagerFactory: EntityManagerFactory,
        private val dogClass: Class<*>,
        private val catClass: Class<*>
    ) {
        fun findCat(catId: UUID): Any? {
            return find(catId, catClass)
        }

        fun findDog(dogId: UUID): Any? {
            return find(dogId, dogClass)
        }

        fun find(id: Any, clazz: Class<*>): Any? {
            entityManagerFactory.createEntityManager().use {
                return it.find(clazz, id)
            }
        }

        fun persist(any: Any) {
            entityManagerFactory.createEntityManager().transaction {
                it.persist(any)
            }
        }
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

    private fun createRequest(holdingId: net.corda.virtualnode.HoldingIdentity, entity: Any): EntityRequest {
        val flowKey = FlowKey(UUID.randomUUID().toString(), holdingId.toAvro())
        logger.info("Entity Request - flow: $flowKey, entity: $entity")
        return EntityRequest(Instant.now(), flowKey, entity)
    }

    @Test
    fun `create avro messages for E2E testing`() {
        val ctx = createDbTestContext()

        // saving binary for E2E testing
        // dog
        val dogId = UUID.randomUUID()
        val dog = ctx.sandbox.createDogInstance(dogId, "Pluto", Instant.now(), "me")
        FileOutputStream("/tmp/avro/pluto-dog.amqp.bin").use {
            it.channel.write(
                ByteBuffer.wrap(ctx.sandbox.getSerializer().serialize(dog).bytes))
        }

        // cat and owner
        val cat = ctx.sandbox.createCatInstance(
            UUID.randomUUID(),
            "Garfield",
            "ginger",
            UUID.randomUUID(),
            "Jim Davies",
            Calendar.getInstance().get(Calendar.YEAR) - 1976
        )
        FileOutputStream("/tmp/avro/garfield-cat-plus-owner.amqp.bin").use {
            it.channel.write(
                ByteBuffer.wrap(ctx.sandbox.getSerializer().serialize(cat).bytes))
        }

        // find dog
//        val serializedDogId = ctx.sandbox.getSerializer().serialize(dogId)
//        FileOutputStream("/tmp/avro/find-dog.avro.bin").use {
//            it.channel.write(
//                FindEntity(DOG_CLASS_NAME, ByteBuffer.wrap(serializedDogId.bytes)).toByteBuffer())
//        }

        // update dog
        val bellaTheDog = ctx.sandbox.createDogInstance(dogId, "Bella", Instant.now(), "me")
        FileOutputStream("/tmp/avro/bello-dog.amqp.bin").use {
            it.channel.write(
                ByteBuffer.wrap(ctx.sandbox.getSerializer().serialize(bellaTheDog).bytes))
        }

        // delete dog
//        FileOutputStream("/tmp/avro/update-dog.avro.bin").use {
//            it.channel.write(
//                DeleteEntity(ByteBuffer.wrap(ctx.sandbox.getSerializer().serialize(bellaTheDog).bytes)).toByteBuffer())
//        }
    }
}
