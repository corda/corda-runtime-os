package net.corda.flow.testing.context

import com.typesafe.config.ConfigFactory
import net.corda.cpiinfo.read.fake.CpiInfoReadServiceFake
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseFailure
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.data.persistence.Error
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.flow.testing.fakes.FakeFlowFiberFactory
import net.corda.flow.testing.fakes.FakeMembershipGroupReaderProvider
import net.corda.flow.testing.fakes.FakeSandboxGroupContextComponent
import net.corda.flow.testing.tests.FLOW_NAME
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.ManifestCorDappInfo
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.configuration.FlowConfig
import net.corda.test.flow.util.buildSessionEvent
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.fake.VirtualNodeInfoReadServiceFake
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

@Suppress("Unused")
@Component(service = [FlowServiceTestContext::class])
class FlowServiceTestContext @Activate constructor(
    @Reference(service = VirtualNodeInfoReadServiceFake::class)
    val virtualNodeInfoReadService: VirtualNodeInfoReadServiceFake,
    @Reference(service = CpiInfoReadServiceFake::class)
    val cpiInfoReadService: CpiInfoReadServiceFake,
    @Reference(service = FakeSandboxGroupContextComponent::class)
    val sandboxGroupContextComponent: FakeSandboxGroupContextComponent,
    @Reference(service = FakeMembershipGroupReaderProvider::class)
    val membershipGroupReaderProvider: FakeMembershipGroupReaderProvider,
    @Reference(service = FlowEventProcessorFactory::class)
    val eventProcessorFactory: FlowEventProcessorFactory,
    @Reference(service = FakeFlowFiberFactory::class)
    val flowFiberFactory: FakeFlowFiberFactory,
) : StepSetup, ThenSetup {

    private companion object {
        val log = contextLogger()
    }

    private val testConfig = mutableMapOf<String, Any>(
        FlowConfig.CRYPTO_MAX_RETRIES to 2,
        FlowConfig.CRYPTO_MESSAGE_RESEND_WINDOW to 500000L,
        FlowConfig.PERSISTENCE_MAX_RETRIES to 2,
        FlowConfig.PERSISTENCE_MESSAGE_RESEND_WINDOW to 500000L,
        FlowConfig.SESSION_MESSAGE_RESEND_WINDOW to 500000L,
        FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW to 500000L,
        FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS to 5,
        FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION to 60000,
        FlowConfig.PROCESSING_MAX_RETRY_DELAY to 16000,
        FlowConfig.PROCESSING_MAX_FLOW_EXECUTION_DURATION to 60000
    )

    private val testRuns = mutableListOf<TestRun>()
    private val assertions = mutableListOf<OutputAssertionsImpl>()
    private var lastPublishedState: Checkpoint? = null
    private var sessionInitiatingIdentity: HoldingIdentity? = null
    private var sessionInitiatedIdentity: HoldingIdentity? = null

    fun start() {
        virtualNodeInfoReadService.start()
        cpiInfoReadService.start()

        virtualNodeInfoReadService.waitUntilRunning()
        cpiInfoReadService.waitUntilRunning()
    }

    override val initiatedIdentityMemberName: MemberX500Name
        get() = MemberX500Name.parse(sessionInitiatedIdentity!!.x500Name)

    override fun virtualNode(cpiId: String, holdingId: HoldingIdentity) {
        val emptyUUID = UUID(0, 0)

        virtualNodeInfoReadService.addOrUpdate(
            VirtualNodeInfo(
                holdingId.toCorda(),
                getCpiIdentifier(cpiId),
                emptyUUID,
                emptyUUID,
                emptyUUID,
                emptyUUID,
                timestamp = Instant.now()
            )
        )
    }

    override fun cpkMetadata(cpiId: String, cpkId: String, cpkChecksum: SecureHash) {
        val manifestCordAppInfo = ManifestCorDappInfo(null, null, null, null)

        val cordAppManifest = CordappManifest(
            "",
            "",
            0,
            0,
            manifestCordAppInfo,
            manifestCordAppInfo,
            mapOf()
        )

        val timestamp = Instant.now()
        val cpkMeta = CpkMetadata(
            getCpkIdentifier(cpkId),
            CpkManifest(CpkFormatVersion(0, 0)),
            "",
            listOf(),
            listOf(),
            cordAppManifest,
            CpkType.UNKNOWN,
            cpkChecksum,
            setOf(),
            timestamp
        )

        val cpiMeta = CpiMetadata(
            getCpiIdentifier(cpiId),
            getSecureHash(),
            listOf(cpkMeta),
            "",
            -1,
            timestamp
        )

        cpiInfoReadService.addOrUpdate(cpiMeta)
    }

    override fun sandboxCpk(cpkFileChecksum: SecureHash) {
        sandboxGroupContextComponent.putCpk(cpkFileChecksum)
    }

    override fun membershipGroupFor(owningMember: HoldingIdentity) {
        membershipGroupReaderProvider.put(owningMember.toCorda())
    }

    override fun sessionInitiatingIdentity(initiatingIdentity: HoldingIdentity) {
        this.sessionInitiatingIdentity = initiatingIdentity
    }

    override fun sessionInitiatedIdentity(initiatedIdentity: HoldingIdentity) {
        this.sessionInitiatedIdentity = initiatedIdentity
    }

    override fun flowConfiguration(key: String, value: Any) {
        testConfig[key] = value
    }

    override fun initiatingToInitiatedFlow(
        protocol: String,
        initiatingFlowClassName: String,
        initiatedFlowClassName: String
    ) {
        sandboxGroupContextComponent.initiatingToInitiatedFlowPair(
            protocol,
            initiatingFlowClassName,
            initiatedFlowClassName
        )
    }

    override fun startFlowEventReceived(
        flowId: String,
        requestId: String,
        holdingId: HoldingIdentity,
        cpiId: String,
        args: String
    ): FlowIoRequestSetup {
        val flowStart = FlowStartContext.newBuilder().apply {
            this.statusKey = FlowKey(requestId, holdingId)
            this.requestId = requestId
            this.identity = holdingId
            this.initiatedBy = holdingId
            this.cpiId = cpiId
            this.initiatorType = FlowInitiatorType.RPC
            this.flowClassName = FLOW_NAME
            this.createdTimestamp = Instant.now()
        }.build()

        return addTestRun(createFlowEventRecord(flowId, StartFlow(flowStart, "{}")))
    }

    override fun sessionInitEventReceived(
        flowId: String,
        sessionId: String,
        cpiId: String,
        protocol: String,
        initiatingIdentity: HoldingIdentity?,
        initiatedIdentity: HoldingIdentity?
    ): FlowIoRequestSetup {
        return createAndAddSessionEvent(
            flowId,
            sessionId,
            initiatingIdentity,
            initiatedIdentity,
            SessionInit.newBuilder()
                .setProtocol(protocol)
                .setVersions(listOf(1))
                .setFlowId(flowId)
                .setCpiId(cpiId)
                .setPayload(ByteBuffer.wrap(byteArrayOf()))
                .build(),
            sequenceNum = 0,
            receivedSequenceNum = 1,
        )
    }

    override fun sessionAckEventReceived(
        flowId: String,
        sessionId: String,
        receivedSequenceNum: Int,
        initiatingIdentity: HoldingIdentity?,
        initiatedIdentity: HoldingIdentity?
    ): FlowIoRequestSetup {
        return createAndAddSessionEvent(
            flowId,
            sessionId,
            initiatingIdentity,
            initiatedIdentity,
            SessionAck(),
            sequenceNum = null,
            receivedSequenceNum,
        )
    }

    override fun sessionDataEventReceived(
        flowId: String,
        sessionId: String,
        data: ByteArray,
        sequenceNum: Int,
        receivedSequenceNum: Int,
        initiatingIdentity: HoldingIdentity?,
        initiatedIdentity: HoldingIdentity?
    ): FlowIoRequestSetup {
        return createAndAddSessionEvent(
            flowId,
            sessionId,
            initiatingIdentity,
            initiatedIdentity,
            SessionData(ByteBuffer.wrap(data)),
            sequenceNum,
            receivedSequenceNum,
        )
    }

    override fun sessionCloseEventReceived(
        flowId: String,
        sessionId: String,
        sequenceNum: Int,
        receivedSequenceNum: Int,
        initiatingIdentity: HoldingIdentity?,
        initiatedIdentity: HoldingIdentity?
    ): FlowIoRequestSetup {
        return createAndAddSessionEvent(
            flowId,
            sessionId,
            initiatingIdentity,
            initiatedIdentity,
            SessionClose(),
            sequenceNum,
            receivedSequenceNum,
        )
    }

    override fun sessionErrorEventReceived(
        flowId: String,
        sessionId: String,
        sequenceNum: Int,
        receivedSequenceNum: Int,
        initiatingIdentity: HoldingIdentity?,
        initiatedIdentity: HoldingIdentity?
    ): FlowIoRequestSetup {
        return createAndAddSessionEvent(
            flowId,
            sessionId,
            initiatingIdentity,
            initiatedIdentity,
            SessionError(ExceptionEnvelope(RuntimeException::class.qualifiedName, "Something went wrong!")),
            sequenceNum,
            receivedSequenceNum,
        )
    }

    override fun wakeupEventReceived(flowId: String): FlowIoRequestSetup {
        return addTestRun(createFlowEventRecord(flowId, Wakeup()))
    }

    override fun cryptoSignResponseReceived(
        flowId: String,
        requestId: String,
        publicKey: PublicKey,
        bytes: ByteArray,
        requestingComponent: String,
        requestingTimestamp: Instant,
        responseTimestamp: Instant,
        tenantId: String,
        otherContext: KeyValuePairList,
        exceptionEnvelope: ExceptionEnvelope?
    ): FlowIoRequestSetup {
        val context = CryptoResponseContext.newBuilder()
            .setRequestingComponent("Flow Worker")
            .setRequestTimestamp(requestingTimestamp)
            .setRequestId(requestId)
            .setResponseTimestamp(responseTimestamp)
            .setTenantId(tenantId)
            .setOther(otherContext)
            .build()

        context.other.items.add(
            KeyValuePair(
                CryptoFlowOpsTransformer.REQUEST_OP_KEY,
                SignFlowCommand::class.java.simpleName
            )
        )

        return addTestRun(
            createFlowEventRecord(
                flowId,
                FlowOpsResponse(
                    CryptoResponseContext.newBuilder()
                        .setRequestingComponent("Flow Worker")
                        .setRequestTimestamp(requestingTimestamp)
                        .setRequestId(requestId)
                        .setResponseTimestamp(responseTimestamp)
                        .setTenantId(tenantId)
                        .setOther(otherContext)
                        .build(),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(publicKey.encoded),
                        ByteBuffer.wrap(bytes),
                        KeyValuePairList(mutableListOf())
                    ),
                    exceptionEnvelope
                )
            )
        )
    }

    override fun entityResponseSuccessReceived(
        flowId: String,
        requestId: String,
        byteBuffer: ByteBuffer?
    ): FlowIoRequestSetup {
        return addEntityResponseToTestRun(requestId, EntityResponseSuccess(byteBuffer), flowId)
    }

    override fun entityResponseErrorReceived(
        flowId: String,
        requestId: String,
        errorType: Error,
        exception: ExceptionEnvelope
    ): FlowIoRequestSetup {
        return addEntityResponseToTestRun(requestId, EntityResponseFailure(errorType, exception), flowId)
    }

    private fun addEntityResponseToTestRun(
        requestId: String,
        entityResponsePayload: Any,
        flowId: String
    ): FlowIoRequestSetup {
        val entityResponse = EntityResponse.newBuilder()
            .setRequestId(requestId)
            .setTimestamp(Instant.now())
            .setResponseType(entityResponsePayload)
            .build()

        return addTestRun(createFlowEventRecord(flowId, entityResponse))
    }

    override fun expectOutputForFlow(flowId: String, outputAssertions: OutputAssertions.() -> Unit) {
        val assertionsCapture = OutputAssertionsImpl(flowId, sessionInitiatingIdentity, sessionInitiatedIdentity)
        assertions.add(assertionsCapture)
        outputAssertions(assertionsCapture)
    }

    fun clearTestRuns() {
        testRuns.clear()
    }

    fun clearAssertions() {
        assertions.clear()
    }

    fun execute() {
        val flowEventProcessor = getFlowEventProcessor()

        testRuns.forEachIndexed { iteration, testRun ->
            log.info("Start test run for input/output set $iteration")
            flowFiberFactory.fiber.reset()
            flowFiberFactory.fiber.ioToCompleteWith = testRun.ioRequest
            val response = flowEventProcessor.onNext(lastPublishedState, testRun.event)
            testRun.flowContinuation = flowFiberFactory.fiber.flowContinuation
            testRun.response = response
            lastPublishedState = response.updatedState
        }
    }

    fun assert() {
        assertEquals(
            assertions.size,
            testRuns.size,
            "number of output assertions does not match the number of input events"
        )
        var index = 0
        testRuns.forEach {
            assertions[index++].asserts.forEach { assert -> assert(it) }
        }
    }

    fun resetTestContext() {
        testRuns.clear()
        assertions.clear()
        lastPublishedState = null
        sessionInitiatingIdentity = null
        sessionInitiatedIdentity = null

        virtualNodeInfoReadService.reset()
        cpiInfoReadService.reset()
        sandboxGroupContextComponent.reset()
        membershipGroupReaderProvider.reset()
    }

    private fun createAndAddSessionEvent(
        flowId: String,
        sessionId: String,
        initiatingIdentity: HoldingIdentity?,
        initiatedIdentity: HoldingIdentity?,
        payload: Any,
        sequenceNum: Int?,
        receivedSequenceNum: Int?
    ): FlowIoRequestSetup {
        val sessionEvent = buildSessionEvent(
            MessageDirection.INBOUND,
            sessionId,
            sequenceNum,
            payload,
            receivedSequenceNum ?: sequenceNum ?: 0,
            listOf(0),
            Instant.now(),
            initiatingIdentity ?: sessionInitiatingIdentity!!,
            initiatedIdentity ?: sessionInitiatedIdentity!!
        )
        return addTestRun(createFlowEventRecord(flowId, sessionEvent))
    }

    private fun getFlowEventProcessor(): StateAndEventProcessor<String, Checkpoint, FlowEvent> {
        val cfg = ConfigFactory.parseMap(testConfig)
        return eventProcessorFactory.create(
            SmartConfigFactory
                .create(cfg)
                .create(cfg)
        )
    }

    private fun createFlowEventRecord(key: String, payload: Any): Record<String, FlowEvent> {
        return Record(FLOW_EVENT_TOPIC, key, FlowEvent(key, payload))
    }

    private fun getCpiIdentifier(cpiId: String): CpiIdentifier {
        return CpiIdentifier(
            cpiId,
            "0.0",
            getSecureHash()
        )
    }

    private fun getCpkIdentifier(cpkId: String): CpkIdentifier {
        return CpkIdentifier(
            cpkId,
            "0.0",
            getSecureHash()
        )
    }

    private fun getSecureHash(): SecureHash {
        return SecureHash("ALG", byteArrayOf(0, 0, 0, 0))
    }

    private fun addTestRun(eventRecord: Record<String, FlowEvent>): FlowIoRequestSetup {
        val testRun = TestRun(eventRecord)
        testRuns.add(testRun)

        return object : FlowIoRequestSetup {

            override fun suspendsWith(flowIoRequest: FlowIORequest<*>) {
                testRun.ioRequest = FlowIORequest.FlowSuspended(ByteBuffer.wrap(byteArrayOf()), flowIoRequest)
            }

            override fun completedSuccessfullyWith(result: String?) {
                testRun.ioRequest = FlowIORequest.FlowFinished(result)
            }

            override fun completedWithError(exception: Exception) {
                testRun.ioRequest = FlowIORequest.FlowFailed(exception)
            }
        }
    }
}
