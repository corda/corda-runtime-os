package net.corda.flow.pipeline.impl

import java.time.Instant
import net.corda.data.KeyValuePairList
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.identity.HoldingIdentity
import net.corda.test.flow.util.buildSessionEvent
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.MDC_EXTERNAL_EVENT_ID
import net.corda.utilities.MDC_FLOW_ID
import net.corda.utilities.MDC_SESSION_EVENT_ID
import net.corda.utilities.MDC_VNODE_ID

class FlowMDCServiceImplTest {
    private val flowMDCService = FlowMDCServiceImpl()
    private val flowKey = "flowId"
    private val startRequestId = "requestId"
    private val sessionId = "sessionId"
    private val externalRequestId = "externalRequestId"
    private val payload = ExternalEventResponse()
    private val aliceHoldingIdentity = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "1")
    private val aliceShortHash = aliceHoldingIdentity.toCorda().shortHash.toString()
    private val startFlowEvent = StartFlow(
        FlowStartContext(
            FlowKey(flowKey, aliceHoldingIdentity),
            FlowInitiatorType.RPC,
            startRequestId,
            aliceHoldingIdentity,
            "cpiId",
            aliceHoldingIdentity,
            "flowClassName",
            "startArgs",
            KeyValuePairList(),
            Instant.now()
        ),
        "flowStartArgs"
    )

    private fun flowEvent(payload: Any) = FlowEvent.newBuilder()
        .setFlowId(flowKey)
        .setPayload(payload)
        .build()

    private fun buildCheckpoint(includeExternalEventState: Boolean): Checkpoint {
        val checkpoint = Checkpoint()
        val flowState = FlowState()
        val startContext = FlowStartContext()
        val externalEventState = ExternalEventState()
        checkpoint.flowState = flowState
        flowState.flowStartContext = startContext
        if (includeExternalEventState) {
            flowState.externalEventState = externalEventState
        }
        startContext.identity = aliceHoldingIdentity
        startContext.requestId = startRequestId

        externalEventState.requestId = externalRequestId

        return checkpoint
    }

    private fun buildSessionEvent(payload: Any) =
        buildSessionEvent(
            MessageDirection.INBOUND,
            sessionId,
            1,
            payload,
            Instant.now(),
            aliceHoldingIdentity,
            aliceHoldingIdentity,
            null
        )

    @Test
    fun `Verify MDC from no checkpoint with startFlow`() {
        val mdc = flowMDCService.getMDCLogging(null, flowEvent(startFlowEvent), flowKey)

        assertThat(mdc.size).isEqualTo(3)
        assertThat(mdc[MDC_VNODE_ID]).isEqualTo(aliceShortHash)
        assertThat(mdc[MDC_CLIENT_ID]).isEqualTo(startRequestId)
        assertThat(mdc[MDC_FLOW_ID]).isEqualTo(flowKey)
    }

    @Test
    fun `Verify MDC from no checkpoint with SessionEvent`() {
        val mdc =
            flowMDCService.getMDCLogging(null, flowEvent(buildSessionEvent(SessionInit())), flowKey)

        assertThat(mdc.size).isEqualTo(4)
        assertThat(mdc[MDC_VNODE_ID]).isEqualTo(aliceShortHash)
        assertThat(mdc[MDC_CLIENT_ID]).isEqualTo(sessionId)
        assertThat(mdc[MDC_SESSION_EVENT_ID]).isEqualTo(sessionId)
        assertThat(mdc[MDC_FLOW_ID]).isEqualTo(flowKey)
    }

    @Test
    fun `Verify MDC from no checkpoint with Wakeup`() {
        val mdc =
            flowMDCService.getMDCLogging(null, flowEvent(payload), flowKey)

        assertThat(mdc.size).isEqualTo(1)
        assertThat(mdc[MDC_FLOW_ID]).isEqualTo(flowKey)
    }

    @Test
    fun `Verify MDC from checkpoint Wakeup with ExternalEvent set`() {
        val mdc = flowMDCService.getMDCLogging(buildCheckpoint(true), flowEvent(payload), flowKey)

        assertThat(mdc.size).isEqualTo(4)
        assertThat(mdc[MDC_VNODE_ID]).isEqualTo(aliceShortHash)
        assertThat(mdc[MDC_CLIENT_ID]).isEqualTo(startRequestId)
        assertThat(mdc[MDC_EXTERNAL_EVENT_ID]).isEqualTo(externalRequestId)
        assertThat(mdc[MDC_FLOW_ID]).isEqualTo(flowKey)
    }

    @Test
    fun `Verify MDC from checkpoint SessionEvent with ExternalEvent set`() {
        val mdc = flowMDCService.getMDCLogging(buildCheckpoint(true), flowEvent(buildSessionEvent(SessionData())), flowKey)

        assertThat(mdc.size).isEqualTo(5)
        assertThat(mdc[MDC_VNODE_ID]).isEqualTo(aliceShortHash)
        assertThat(mdc[MDC_CLIENT_ID]).isEqualTo(startRequestId)
        assertThat(mdc[MDC_SESSION_EVENT_ID]).isEqualTo(sessionId)
        assertThat(mdc[MDC_EXTERNAL_EVENT_ID]).isEqualTo(externalRequestId)
        assertThat(mdc[MDC_FLOW_ID]).isEqualTo(flowKey)
    }

    @Test
    fun `Verify MDC from checkpoint Wakeup with no ExternalEvent set`() {
        val mdc = flowMDCService.getMDCLogging(buildCheckpoint(false), flowEvent(payload), flowKey)

        assertThat(mdc.size).isEqualTo(3)
        assertThat(mdc[MDC_VNODE_ID]).isEqualTo(aliceShortHash)
        assertThat(mdc[MDC_CLIENT_ID]).isEqualTo(startRequestId)
        assertThat(mdc[MDC_FLOW_ID]).isEqualTo(flowKey)
    }
}