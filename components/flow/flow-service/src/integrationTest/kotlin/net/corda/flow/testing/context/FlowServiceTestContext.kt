package net.corda.flow.testing.context

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.flow.testing.fakes.FakeCpiInfoReadService
import net.corda.flow.testing.fakes.FakeFlowFiberFactory
import net.corda.flow.testing.fakes.FakeMembershipGroupReaderProvider
import net.corda.flow.testing.fakes.FakeSandboxGroupContextComponent
import net.corda.flow.testing.fakes.FakeVirtualNodeInfoReadService
import net.corda.flow.testing.tests.FLOW_NAME
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.libs.packaging.CpkIdentifier
import net.corda.libs.packaging.CpkMetadata
import net.corda.messaging.api.records.Record
import net.corda.packaging.CPK
import net.corda.packaging.CordappManifest
import net.corda.packaging.ManifestCordappInfo
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.configuration.FlowConfig
import net.corda.test.flow.util.buildSessionEvent
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*

@Suppress("Unused")
@Component(service = [FlowServiceTestContext::class], scope = ServiceScope.PROTOTYPE)
class FlowServiceTestContext @Activate constructor(
    @Reference(service = FakeVirtualNodeInfoReadService::class)
    val virtualNodeInfoReadService: FakeVirtualNodeInfoReadService,
    @Reference(service = FakeCpiInfoReadService::class)
    val cpiInfoReadService: FakeCpiInfoReadService,
    @Reference(service = FakeSandboxGroupContextComponent::class)
    val sandboxGroupContextComponent: FakeSandboxGroupContextComponent,
    @Reference(service = FakeMembershipGroupReaderProvider::class)
    val membershipGroupReaderProvider: FakeMembershipGroupReaderProvider,
    @Reference(service = FlowEventProcessorFactory::class)
    val eventProcessorFactory: FlowEventProcessorFactory,
    @Reference(service = FakeFlowFiberFactory::class)
    val flowFiberFactory: FakeFlowFiberFactory,
) : GivenSetup, WhenSetup, ThenSetup {

    private val testRuns = mutableListOf<TestRun>()
    private val assertions = mutableListOf<OutputAssertionsImpl>()
    private var lastPublishedState: Checkpoint? = null
    private var sessionInitiatingIdentity: net.corda.data.identity.HoldingIdentity? = null
    private var sessionInitiatedIdentity: net.corda.data.identity.HoldingIdentity? = null
    private val testConfig = ConfigFactory.empty()
        .withValue(FlowConfig.SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
        .withValue(FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
    private val flowEventProcessor = eventProcessorFactory.create(
        SmartConfigFactory
            .create(testConfig)
            .create(testConfig)
    )

    override val initiatedIdentityMemberName: MemberX500Name
        get() = MemberX500Name.parse(sessionInitiatedIdentity!!.x500Name)

    override fun virtualNode(cpiId: String, holdingIdX500: String, groupId: String) {
        val holdingId = HoldingIdentity(holdingIdX500, groupId)
        val emptyUUID = UUID(0, 0)

        virtualNodeInfoReadService.addVirtualNodeInfo(
            holdingId,
            VirtualNodeInfo(
                holdingId,
                getCpiIdentifier(cpiId),
                emptyUUID,
                emptyUUID,
                emptyUUID,
                emptyUUID
            )
        )
    }

    override fun cpkMetadata(cpiId: String, cpkId: String) {
        val manifestCordAppInfo = ManifestCordappInfo(null, null, null, null)

        val cordAppManifest = CordappManifest(
            "",
            "",
            0,
            0,
            manifestCordAppInfo,
            manifestCordAppInfo,
            mapOf()
        )

        val cpkMeta = CpkMetadata(
            getCpkIdentifier(cpkId),
            CPK.Manifest.newInstance(CPK.FormatVersion.newInstance(0, 0)),
            "",
            listOf(),
            listOf(),
            cordAppManifest,
            CPK.Type.UNKNOWN,
            getSecureHash(),
            setOf()
        )

        val cpiMeta = CpiMetadata(
            getCpiIdentifier(cpiId),
            getSecureHash(),
            listOf(cpkMeta),
            ""
        )

        cpiInfoReadService.add(cpiMeta)
    }

    override fun sandboxCpk(cpkId: String) {
        sandboxGroupContextComponent.putCpk(getCpkIdentifier(cpkId))
    }

    override fun membershipGroupFor(owningMember: net.corda.data.identity.HoldingIdentity) {
        membershipGroupReaderProvider.put(HoldingIdentity(owningMember.x500Name, owningMember.groupId))
    }

    override fun sessionInitiatingIdentity(initiatingIdentity: net.corda.data.identity.HoldingIdentity) {
        this.sessionInitiatingIdentity = initiatingIdentity
    }

    override fun sessionInitiatedIdentity(initiatedIdentity: net.corda.data.identity.HoldingIdentity) {
        this.sessionInitiatedIdentity = initiatedIdentity
    }

    override fun startFlowEventReceived(
        flowId: String,
        requestId: String,
        holdingId: net.corda.data.identity.HoldingIdentity,
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

        return addTestRun(getEventRecord(flowId, StartFlow(flowStart, "{}")))
    }

    override fun sessionAckEventReceived(
        flowId: String,
        sessionId: String,
        sequenceNum: Int,
        initiatingIdentity: net.corda.data.identity.HoldingIdentity?,
        initiatedIdentity: net.corda.data.identity.HoldingIdentity?,
        receivedSequenceNum: Int?
    ): FlowIoRequestSetup {
        return createAndAddSessionEvent(
            flowId,
            sessionId,
            initiatingIdentity,
            initiatedIdentity,
            SessionAck(),
            sequenceNum,
            receivedSequenceNum,
        )
    }

    override fun sessionDataEventReceived(
        flowId: String,
        sessionId: String,
        data: ByteArray,
        sequenceNum: Int,
        initiatingIdentity: net.corda.data.identity.HoldingIdentity?,
        initiatedIdentity: net.corda.data.identity.HoldingIdentity?,
        receivedSequenceNum: Int?
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

    override fun wakeupEventReceived(flowId: String): FlowIoRequestSetup {
        return addTestRun(getEventRecord(flowId, Wakeup()))
    }

    override fun expectOutputForFlow(flowId: String, outputAssertions: OutputAssertions.() -> Unit) {
        val assertionsCapture = OutputAssertionsImpl(flowId, sessionInitiatingIdentity, sessionInitiatedIdentity)
        assertions.add(assertionsCapture)
        outputAssertions(assertionsCapture)
    }

    fun clearTestRuns() {
        testRuns.clear()
    }

    fun execute() {
        testRuns.forEach {
            flowFiberFactory.fiber.reset()
            flowFiberFactory.fiber.ioToCompleteWith = it.ioRequest
            val response = flowEventProcessor.onNext(lastPublishedState, it.event)
            it.flowContinuation = flowFiberFactory.fiber.flowContinuation
            it.response = response
            lastPublishedState = response.updatedState
        }
    }

    fun assert() {
        assertEquals(assertions.size, testRuns.size, "number of output assertions does not match the number of input events")
        var index = 0
        testRuns.forEach {
            assertions[index++].asserts.forEach { assert -> assert(it) }
        }
    }

    fun resetAllServices() {
        virtualNodeInfoReadService.reset()
        cpiInfoReadService.reset()
        sandboxGroupContextComponent.reset()
        membershipGroupReaderProvider.reset()
    }

    private fun createAndAddSessionEvent(
        flowId: String,
        sessionId: String,
        initiatingIdentity: net.corda.data.identity.HoldingIdentity?,
        initiatedIdentity: net.corda.data.identity.HoldingIdentity?,
        payload:Any,
        sequenceNum: Int,
        receivedSequenceNum: Int?
    ): FlowIoRequestSetup {
        val sessionEvent = buildSessionEvent(
            MessageDirection.INBOUND,
            sessionId,
            sequenceNum,
            payload,
            receivedSequenceNum ?: sequenceNum,
            listOf(0),
            Instant.now(),
            initiatingIdentity?:sessionInitiatingIdentity!!,
            initiatedIdentity?:sessionInitiatedIdentity!!
        )
        return  addTestRun(getEventRecord(flowId,sessionEvent))
    }

    private fun getEventRecord(key: String, payload: Any): Record<String, FlowEvent> {
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