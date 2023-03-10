package net.corda.flow.testing.context

import com.typesafe.config.ConfigFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import net.corda.cpiinfo.read.fake.CpiInfoReadServiceFake
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseError
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.flow.testing.fakes.FakeFlowFiberFactory
import net.corda.flow.testing.fakes.FakeMembershipGroupReaderProvider
import net.corda.flow.testing.fakes.FakeSandboxGroupContextComponent
import net.corda.flow.testing.tests.FLOW_NAME
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.flow.utils.keyValuePairListOf
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.configuration.FlowConfig
import net.corda.schema.configuration.MessagingConfig
import net.corda.test.flow.util.buildSessionEvent
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.fake.VirtualNodeInfoReadServiceFake
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("Unused")
@Component(service = [FlowServiceTestContext::class])
class FlowServiceTestContext @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = CpiInfoReadServiceFake::class)
    val cpiInfoReadService: CpiInfoReadServiceFake,
    @Reference(service = FlowEventProcessorFactory::class)
    val eventProcessorFactory: FlowEventProcessorFactory,
    @Reference(service = FakeFlowFiberFactory::class)
    val flowFiberFactory: FakeFlowFiberFactory,
    @Reference(service = FakeMembershipGroupReaderProvider::class)
    val membershipGroupReaderProvider: FakeMembershipGroupReaderProvider,
    @Reference(service = FakeSandboxGroupContextComponent::class)
    val sandboxGroupContextComponent: FakeSandboxGroupContextComponent,
    @Reference(service = VirtualNodeInfoReadServiceFake::class)
    val virtualNodeInfoReadService: VirtualNodeInfoReadServiceFake,
) : StepSetup, ThenSetup {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val testConfig = mutableMapOf<String, Any>(
        FlowConfig.EXTERNAL_EVENT_MAX_RETRIES to 2,
        FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW to 500000L,
        FlowConfig.SESSION_MESSAGE_RESEND_WINDOW to 500000L,
        FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW to 500000L,
        FlowConfig.SESSION_MISSING_COUNTERPARTY_TIMEOUT_WINDOW to 300000L,
        FlowConfig.SESSION_FLOW_CLEANUP_TIME to 30000,
        FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS to 5,
        FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION to 60000,
        FlowConfig.PROCESSING_MAX_RETRY_DELAY to 16000,
        FlowConfig.PROCESSING_FLOW_CLEANUP_TIME to 30000,
        MessagingConfig.Subscription.PROCESSOR_TIMEOUT to 60000,
        MessagingConfig.MAX_ALLOWED_MSG_SIZE to 972800
    )

    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> { }
    private val stringDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, String::class.java)
    private val byteArrayDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, ByteArray::class.java)
    private val anyDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)

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

    override fun virtualNode(cpiId: String, holdingId: HoldingIdentity, flowOperationalStatus: OperationalStatus) {
        val emptyUUID = UUID(0, 0)

        virtualNodeInfoReadService.addOrUpdate(
            VirtualNodeInfo(
                holdingId.toCorda(),
                getCpiIdentifier(cpiId),
                emptyUUID,
                emptyUUID,
                emptyUUID,
                emptyUUID,
                emptyUUID,
                emptyUUID,
                timestamp = Instant.now(),
                flowOperationalStatus = flowOperationalStatus
            )
        )
    }

    override fun cpkMetadata(cpiId: String, cpkId: String, cpkChecksum: SecureHash) {
        val cordAppManifest = CordappManifest(
            "",
            "",
            0,
            0,
            CordappType.WORKFLOW,
            "",
            "",
            0,
            "",
            mapOf()
        )

        val timestamp = Instant.now()
        val cpkMeta = CpkMetadata(
            getCpkIdentifier(cpkId),
            CpkManifest(CpkFormatVersion(0, 0)),
            "",
            listOf(),
            cordAppManifest,
            CpkType.UNKNOWN,
            cpkChecksum,
            setOf(),
            timestamp,
            null
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
        args: String,
        platformContext: Map<String, String>
    ): FlowIoRequestSetup {
        val flowStart = FlowStartContext.newBuilder().apply {
            this.statusKey = FlowKey(requestId, holdingId)
            this.requestId = requestId
            this.identity = holdingId
            this.initiatedBy = holdingId
            this.cpiId = cpiId
            this.initiatorType = FlowInitiatorType.RPC
            this.flowClassName = FLOW_NAME
            this.contextPlatformProperties = keyValuePairListOf(platformContext)
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
                .setContextPlatformProperties(emptyKeyValuePairList())
                .setContextUserProperties(emptyKeyValuePairList())
                .build(),
            sequenceNum = 0,
            receivedSequenceNum = 1,
        )
    }

    override fun sessionAckEventReceived(
        flowId: String,
        sessionId: String,
        receivedSequenceNum: Int,
        outOfOrderSeqNums: List<Int>
    ): FlowIoRequestSetup {
        return createAndAddSessionEvent(
            flowId,
            sessionId,
            null,
            null,
            SessionAck(),
            sequenceNum = null,
            receivedSequenceNum,
            outOfOrderSeqNums
        )
    }

    override fun sessionDataEventReceived(
        flowId: String,
        sessionId: String,
        data: ByteArray,
        sequenceNum: Int,
        receivedSequenceNum: Int,
        outOfOrderSeqNums: List<Int>
    ): FlowIoRequestSetup {
        return createAndAddSessionEvent(
            flowId,
            sessionId,
            null,
            null,
            SessionData(ByteBuffer.wrap(data)),
            sequenceNum,
            receivedSequenceNum,
            outOfOrderSeqNums
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
            null,
            receivedSequenceNum,
        )
    }

    override fun wakeupEventReceived(flowId: String): FlowIoRequestSetup {
        return addTestRun(createFlowEventRecord(flowId, Wakeup()))
    }

    override fun externalEventReceived(flowId: String, requestId: String, payload: Any): FlowIoRequestSetup {
        return addTestRun(
            createFlowEventRecord(
                flowId,
                ExternalEventResponse.newBuilder()
                    .setRequestId(requestId)
                    .setPayload(ByteBuffer.wrap(serializer.serialize(payload)))
                    .setError(null)
                    .setTimestamp(Instant.now())
                    .build()
            )
        )
    }

    override fun externalEventErrorReceived(
        flowId: String,
        requestId: String,
        errorType: ExternalEventResponseErrorType
    ): FlowIoRequestSetup {
        return addTestRun(
            createFlowEventRecord(
                flowId,
                ExternalEventResponse.newBuilder()
                    .setRequestId(requestId)
                    .setPayload(null)
                    .setError(ExternalEventResponseError(errorType, ExceptionEnvelope("type", "message")))
                    .setTimestamp(Instant.now())
                    .build()
            )
        )
    }

    override fun expectOutputForFlow(flowId: String, outputAssertions: OutputAssertions.() -> Unit) {
        val assertionsCapture = OutputAssertionsImpl(
            serializer,
            stringDeserializer,
            byteArrayDeserializer,
            anyDeserializer,
            flowId,
            sessionInitiatingIdentity,
            sessionInitiatedIdentity
        )
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
        receivedSequenceNum: Int?,
        outOfOrderSeqNums: List<Int> = emptyList()
    ): FlowIoRequestSetup {
        val sessionEvent = buildSessionEvent(
            MessageDirection.INBOUND,
            sessionId,
            sequenceNum,
            payload,
            receivedSequenceNum ?: sequenceNum ?: 0,
            outOfOrderSeqNums,
            Instant.now(),
            initiatingIdentity ?: sessionInitiatingIdentity!!,
            initiatedIdentity ?: sessionInitiatedIdentity!!
        )
        return addTestRun(createFlowEventRecord(flowId, sessionEvent))
    }

    private fun getFlowEventProcessor(): StateAndEventProcessor<String, Checkpoint, FlowEvent> {
        val cfg = ConfigFactory.parseMap(testConfig)
        return eventProcessorFactory.create(
            SmartConfigFactory.createWithoutSecurityServices()
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
