package net.corda.entityprocessor.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpk.read.CpkReadService
import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.PersistEntities
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.persistence.testkit.fake.FakeDbConnectionManager
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.db.persistence.testkit.helpers.SandboxHelper.createDog
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.VirtualNodeException
import net.corda.flow.utils.toKeyValuePairList
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.test.util.dsl.entities.cpx.getCpkFileHashes
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.application.flows.FlowContextPropertyKeys.CPK_FILE_CHECKSUM
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
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
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.UUID


/**
 * Test persistence exceptions.  Most of these tests don't require the database
 * set up parts, because we're mocking.
 */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceExceptionTests {
    companion object {
        const val TOPIC = "pretend-topic"
        private const val TIMEOUT_MILLIS = 10000L
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var cpkReadService: CpkReadService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var responseFactory: ResponseFactory

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var currentSandboxGroupContext: CurrentSandboxGroupContext

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        logger.info("Setup test (test Directory: $testDirectory)")
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(TIMEOUT_MILLIS)
            cpiInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
            cpkReadService = setup.fetchService(TIMEOUT_MILLIS)
            virtualNodeInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
            responseFactory = setup.fetchService(TIMEOUT_MILLIS)
        }
    }

    @Test
    fun `exception raised when cpks not present`() {
        val (dbConnectionManager, ignoredRequest) = setupExceptionHandlingTests()

        val entitySandboxService =
            EntitySandboxServiceFactory().create(
                virtualNode.sandboxGroupContextComponent,
                cpkReadService,
                virtualNodeInfoReadService,
                dbConnectionManager
            )

        val processor = EntityMessageProcessor(
            currentSandboxGroupContext,
            entitySandboxService,
            responseFactory,
            this::noOpPayloadCheck
        )

        // Insert some non-existent CPK hashes to our external event to trigger CPK not available error
        ignoredRequest.flowExternalEventContext.contextProperties = KeyValuePairList(
            mutableListOf(
                KeyValuePair("$CPK_FILE_CHECKSUM.0", "SHA-256:DEADBEEF")
            )
        )

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), ignoredRequest)))

        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull
        // The failure is correctly categorised.
        assertThat(response.error.errorType).isEqualTo(ExternalEventResponseErrorType.TRANSIENT)
        // The failure also captures the exception name.
        assertThat(response.error.exception.errorType).isEqualTo(CpkNotAvailableException::class.java.name)

        dbConnectionManager.stop()
    }

    @Test
    fun `exception raised when vnode cannot be found`() {
        val (dbConnectionManager, ignoredRequest) = setupExceptionHandlingTests()
        val brokenVirtualNodeInfoReadService = object :
            VirtualNodeInfoReadService by virtualNodeInfoReadService {
            override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
                throw VirtualNodeException("Placeholder")
            }
        }

        val brokenEntitySandboxService =
            EntitySandboxServiceFactory().create(
                virtualNode.sandboxGroupContextComponent,
                cpkReadService,
                brokenVirtualNodeInfoReadService,
                dbConnectionManager
            )

        val processor = EntityMessageProcessor(
            currentSandboxGroupContext,
            brokenEntitySandboxService,
            responseFactory,
            this::noOpPayloadCheck
        )

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), ignoredRequest)))

        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull
        // The failure is correctly categorised.
        assertThat(response.error.errorType).isEqualTo(ExternalEventResponseErrorType.TRANSIENT)
        // The failure also captures the exception name.
        assertThat(response.error.exception.errorType).isEqualTo(VirtualNodeException::class.java.name)

        dbConnectionManager.stop()
    }

    @Test
    fun `exception raised when sent a missing command`() {
        val (dbConnectionManager, oldRequest) = setupExceptionHandlingTests()
        val unknownCommand = ExceptionEnvelope("", "") // Any Avro object, or null works here.

        val vNodeInfo = virtualNodeInfoReadService.get(oldRequest.holdingIdentity.toCorda())!!
        val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(vNodeInfo)

        val badRequest =
            EntityRequest(
                oldRequest.holdingIdentity,
                unknownCommand,
                ExternalEventContext(
                    "request id",
                    "flow id",
                    cpkFileHashes.toKeyValuePairList(CPK_FILE_CHECKSUM)
                )
            )

        val entitySandboxService =
            EntitySandboxServiceFactory().create(
                virtualNode.sandboxGroupContextComponent,
                cpkReadService,
                virtualNodeInfoReadService,
                dbConnectionManager
            )

        val processor = EntityMessageProcessor(
                currentSandboxGroupContext,
                entitySandboxService,
                responseFactory,
                this::noOpPayloadCheck
            )

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), badRequest)))


        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull
        // The failure is correctly categorised.
        assertThat(response.error.errorType).isEqualTo(ExternalEventResponseErrorType.FATAL)
        // The failure also captures the exception name.
        assertThat(response.error.exception.errorType).isEqualTo(CordaRuntimeException::class.java.name)

        dbConnectionManager.stop()
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
            EntitySandboxServiceFactory().create(
                virtualNode.sandboxGroupContextComponent,
                cpkReadService,
                virtualNodeInfoReadService,
                dbConnectionManager
            )

        val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(virtualNodeInfoOne)
        val sandboxOne = entitySandboxService.get(virtualNodeInfoOne.holdingIdentity, cpkFileHashes)

        // create dog using dog-aware sandbox
        val dog = sandboxOne.createDog("Stray", owner = "Not Known")
        val serialisedDog = sandboxOne.getSerializationService().serialize(dog.instance).bytes

        // create persist request for the sandbox that isn't dog-aware
        val request = EntityRequest(
            virtualNodeInfoOne.holdingIdentity.toAvro(),
            PersistEntities(listOf(ByteBuffer.wrap(serialisedDog))),
            ExternalEventContext("request id", "flow id", KeyValuePairList(emptyList()))
        )
        return Pair(dbConnectionManager, request)
    }
}
