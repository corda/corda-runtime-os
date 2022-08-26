package net.corda.entityprocessor.impl.tests

import java.nio.ByteBuffer
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.PersistEntities
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.entityprocessor.impl.internal.EntitySandboxServiceImpl
import net.corda.entityprocessor.impl.internal.exceptions.NotReadyException
import net.corda.entityprocessor.impl.internal.exceptions.VirtualNodeException
import net.corda.entityprocessor.impl.tests.components.VirtualNodeService
import net.corda.entityprocessor.impl.tests.fake.FakeCpiInfoReadService
import net.corda.entityprocessor.impl.tests.fake.FakeDbConnectionManager
import net.corda.entityprocessor.impl.tests.helpers.BasicMocks
import net.corda.entityprocessor.impl.tests.helpers.Resources
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createDogInstance
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getSerializer
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.messaging.api.records.Record
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension


/**
 * Test persistence exceptions.  Most of these tests don't require the database
 * set up parts, because we're mocking.
 */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceExceptionTests {
    companion object {
        const val TOPIC = "pretend-topic"
        private val logger = contextLogger()
    }

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var externalEventResponseFactory: ExternalEventResponseFactory

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
            externalEventResponseFactory = setup.fetchService(timeout = 5000)
        }
    }

    @Test
    fun `exception raised when cpks not present`() {
        val (dbConnectionManager, ignoredRequest) = setupExceptionHandlingTests()

        // But we need a "broken" service to throw the exception to trigger the new handler.
        // Emulate the throw that occurs if we don't have the cpks
        val brokenCpiInfoReadService = object : FakeCpiInfoReadService() {
            override fun get(identifier: CpiIdentifier): CpiMetadata? {
                throw NotReadyException("Not ready!")
            }
        }

        val brokenEntitySandboxService =
            EntitySandboxServiceImpl(
                virtualNode.sandboxGroupContextComponent,
                brokenCpiInfoReadService,
                virtualNodeInfoReadService,
                dbConnectionManager,
                BasicMocks.componentContext()
            )

        val processor =
            EntityMessageProcessor(brokenEntitySandboxService, externalEventResponseFactory, this::noOpPayloadCheck)

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), ignoredRequest)))

        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull()
        // The failure is correctly categorised.
        assertThat(response.error.errorType).isEqualTo(ExternalEventResponseErrorType.TRANSIENT)
        // The failure also captures the exception name.
        assertThat(response.error.exception.errorType).isEqualTo(NotReadyException::class.java.name)
    }

    @Test
    fun `exception raised when vnode cannot be found`() {
        val (dbConnectionManager, ignoredRequest) = setupExceptionHandlingTests()

        // But we need a "broken" service to throw the exception to trigger the new handler.
        // Emulate the throw that occurs if we don't have the cpks
        val brokenCpiInfoReadService = object : FakeCpiInfoReadService() {
            override fun get(identifier: CpiIdentifier): CpiMetadata? {
                throw VirtualNodeException("Placeholder")
            }
        }

        val brokenEntitySandboxService =
            EntitySandboxServiceImpl(
                virtualNode.sandboxGroupContextComponent,
                brokenCpiInfoReadService,
                virtualNodeInfoReadService,
                dbConnectionManager,
                BasicMocks.componentContext()
            )

        val processor =
            EntityMessageProcessor(brokenEntitySandboxService, externalEventResponseFactory, this::noOpPayloadCheck)

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), ignoredRequest)))

        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull()
        // The failure is correctly categorised.
        assertThat(response.error.errorType).isEqualTo(ExternalEventResponseErrorType.TRANSIENT)
        // The failure also captures the exception name.
        assertThat(response.error.exception.errorType).isEqualTo(VirtualNodeException::class.java.name)
    }

    @Test
    fun `exception raised when sent a missing command`() {
        val (dbConnectionManager, oldRequest) = setupExceptionHandlingTests()
        val unknownCommand = ExceptionEnvelope("", "") // Any Avro object, or null works here.
        val badRequest =
            EntityRequest(oldRequest.holdingIdentity, unknownCommand, ExternalEventContext("request id", "flow id"))

        val entitySandboxService =
            EntitySandboxServiceImpl(
                virtualNode.sandboxGroupContextComponent,
                cpiInfoReadService,
                virtualNodeInfoReadService,
                dbConnectionManager,
                BasicMocks.componentContext()
            )

        val processor =
            EntityMessageProcessor(entitySandboxService, externalEventResponseFactory, this::noOpPayloadCheck)

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), badRequest)))

        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull()
        // The failure is correctly categorised.
        assertThat(response.error.errorType).isEqualTo(ExternalEventResponseErrorType.FATAL)
        // The failure also captures the exception name.
        assertThat(response.error.exception.errorType).isEqualTo(CordaRuntimeException::class.java.name)
    }

    private fun noOpPayloadCheck(bytes: ByteBuffer) = bytes

    /**
     * Create a simple request and return it, and the (fake) db connection manager.
     */
    private fun setupExceptionHandlingTests(): Pair<FakeDbConnectionManager, EntityRequest> {
        val virtualNodeInfoOne = virtualNode.load(Resources.EXTENDABLE_CPB)
        val animalDbConnection = Pair(virtualNodeInfoOne.vaultDmlConnectionId, "animals-node")
        val dbConnectionManager = FakeDbConnectionManager(
            listOf(animalDbConnection),
            "PersistenceExceptionTests"
        )

        // We need a 'working' service to set up the test
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
        val request = EntityRequest(
            virtualNodeInfoOne.holdingIdentity.toAvro(),
            PersistEntities(listOf(ByteBuffer.wrap(serialisedDog))),
            ExternalEventContext("request id", "flow id")
        )
        return Pair(dbConnectionManager, request)
    }
}
