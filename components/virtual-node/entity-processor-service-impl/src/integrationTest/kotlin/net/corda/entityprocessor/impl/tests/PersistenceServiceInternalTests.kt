package net.corda.entityprocessor.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.virtualnode.PersistEntity
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.entityprocessor.impl.internal.EntitySandboxContextTypes
import net.corda.entityprocessor.impl.internal.EntitySandboxServiceImpl
import net.corda.entityprocessor.impl.internal.PersistenceServiceInternal
import net.corda.entityprocessor.impl.tests.components.VirtualNodeService
import net.corda.entityprocessor.impl.tests.helpers.BasicMocks
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.testing.cpks.dogs.Dog
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.application.serialization.SerializationService
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions
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
 * You might need to have a running database:
 *
 *     docker run --rm --name test-instance -e POSTGRES_PASSWORD=password -p 5432:5432 postgres
 *
 *     gradlew integrationTest -PpostgresPort=5432
 */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceServiceInternalTests {
    companion object {
        const val TOPIC = "not used"
    }

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

        val serializer = sandbox.getObjectByKey<SerializationService>(EntitySandboxContextTypes.SANDBOX_SERIALIZER)
        Assertions.assertThat(serializer).isNotNull

        val expectedDog = Dog(UUID.randomUUID(), "rover", Instant.now(), "me")
        val dogBytes = serializer!!.serialize(expectedDog)

        val persistenceService = PersistenceServiceInternal(entitySandboxService)
        val payload = PersistEntity(ByteBuffer.wrap(dogBytes.bytes))

        val entityManager = BasicMocks.entityManager()

        persistenceService.persist(serializer, entityManager, payload)

        Mockito.verify(entityManager).persist(Mockito.any())

    }

    //@Test
//     fun `persist via message processor`() {
//         val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)
//        val entitySandboxService =
//            EntitySandboxServiceImpl(
//                virtualNode.sandboxGroupContextComponent,
//                cpiInfoReadService,
//                virtualNodeInfoReadService,
//                BasicMocks.dbConnectionManager(),
//                BasicMocks.componentContext()
//            )
//
//        val sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity)
//        val serializer = sandbox.getObjectByKey<SerializationService>(EntitySandboxContextTypes.SANDBOX_SERIALIZER)
//
//        val flowKey = FlowKey(UUID.randomUUID().toString(), virtualNodeInfo.holdingIdentity.toAvro())
//        val expectedDog = Dog(UUID.randomUUID(), "rover", Instant.now(), "me")
//        val dogBytes = serializer!!.serialize(expectedDog)
//        val persistEntity = PersistEntity(ByteBuffer.wrap(dogBytes.bytes))
//        val request = EntityRequest(Instant.now(), flowKey, persistEntity)
//        val processor = EntityMessageProcessor(entitySandboxService)
//
//        val requestId = UUID.randomUUID().toString() // just needs to be something unique.
//        val records = listOf(Record(TOPIC, requestId, request))
//        val responses = processor.onNext(records)
//
//        assertThat(responses.size).isEqualTo(1)
//        assertThat(responses.first().value.requestId).isEqualTo(requestId)
//    }

//    fun `persist using two different sandboxes`() {
//        val virtualNodeInfoOne = virtualNode.load(Resources.EXTENDABLE_CPB)
//        val virtualNodeInfoTwo = virtualNode.load(Resources.EXTENDABLE_CPB)
//
//        // should be able to test sandboxes in here, such as entities in one can't be persisted by two
//    }

    // fun `persist to an actual database`() {
//  // same as persist via message processor, but with real db back-end

    // assertThat(responses.size).isEqualTo(1)

    // val queryResults = someActualDbQuery("select * from Dog")
    // assertThat(queryResults.size).isEqualTo(1)
    // assertThat(queryResults.first().name).isEqualTo(dog.name)
//
// }
}
