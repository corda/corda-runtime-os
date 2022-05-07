package net.corda.entityprocessor.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.FlowKey
import net.corda.data.virtualnode.EntityRequest
import net.corda.data.virtualnode.EntityResponse
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
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createDogClass
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createSerializedDog
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getSerializer
import net.corda.messaging.api.records.Record
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.utils.use
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
import java.time.Instant
import java.util.UUID

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
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        logger.info("Setup test (test Directory: $testDirectory)")
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(timeout = 1000)
            cpiInfoReadService = setup.fetchService(timeout = 1000)
            virtualNodeInfoReadService = setup.fetchService(timeout = 1000)
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
        val payload = PersistEntity(
            ByteBuffer.wrap(sandbox.createSerializedDog(dogId, "Rover", Instant.now(), "me")))

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

        val request = dogRequest(virtualNodeInfo, sandbox, UUID.randomUUID(), "Walter", Instant.now(), "me")
        val processor = EntityMessageProcessor(entitySandboxService)

        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, request))
        val responses = processor.onNext(records)

        assertThat(responses.size).isEqualTo(1)
        val response = processor.onNext(records).first().value as EntityResponse
        assertThat(response.requestId).isEqualTo(requestId)
    }

//    fun `persist using two different sandboxes`() {
//        val virtualNodeInfoOne = virtualNode.load(Resources.EXTENDABLE_CPB)
//        val virtualNodeInfoTwo = virtualNode.load(Resources.EXTENDABLE_CPB)
//
//        // should be able to test sandboxes in here, such as entities in one can't be persisted by two
//    }

    @Test
    fun `persist to an actual database`() {
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)

        val dogDbConnection = Pair(virtualNodeInfo.vaultDmlConnectionId, "dogs-node")
        val dbConnectionManager = FakeDbConnectionManager(listOf(dogDbConnection))

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

        val dogClass = sandbox.sandboxGroup.createDogClass()
        val cl = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    dogClass.packageName, listOf("migration/db.changelog-master.xml"),
                    classLoader = sandbox.sandboxGroup.createDogClass().classLoader
                )
            )
        )

        lbm.updateDb(dbConnectionManager.getDataSource(dogDbConnection.first).connection, cl)

        // request persist
        val dogId = UUID.randomUUID()
        val request = dogRequest(
            virtualNodeInfo,
            sandbox,
            dogId,
            "Pluto",
            Instant.now(),
            "me")
        val processor = EntityMessageProcessor(entitySandboxService)
        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
        val records = listOf(Record(TOPIC, requestId, request))
        val responses = processor.onNext(records)

        // assert persisted
        assertThat(responses.size).isEqualTo(1)

        val queryResults = dbConnectionManager.createEntityManagerFactory(
            dogDbConnection.first, JpaEntitiesSet.create(dogDbConnection.second, setOf(dogClass))
        ).createEntityManager().use {
            it.find(dogClass, dogId)
        }

        assertThat(queryResults).isNotNull
        logger.info("Woof ${queryResults}")
    }


    private fun dogRequest(
        virtualNodeInfo: VirtualNodeInfo,
        sandbox: SandboxGroupContext,
        dogId: UUID,
        name: String,
        bday: Instant,
        owner: String,
    ): EntityRequest {
        val flowKey = FlowKey(UUID.randomUUID().toString(), virtualNodeInfo.holdingIdentity.toAvro())
        val persistEntity = PersistEntity(ByteBuffer.wrap(sandbox.createSerializedDog(dogId, name, bday, owner)))
        logger.info("Entity Request - flow: $flowKey, entity: $dogId, $name")
        return EntityRequest(Instant.now(), flowKey, persistEntity)
    }
}
