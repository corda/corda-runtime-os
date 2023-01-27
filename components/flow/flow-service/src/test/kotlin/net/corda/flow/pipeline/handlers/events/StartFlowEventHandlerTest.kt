package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.handlers.waiting.WaitingForStartFlow
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StartFlowEventHandlerTest {
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val startFlow = StartFlow(FlowStartContext(), "start args")
    private val flowId = "flow id"
    private val handler = StartFlowEventHandler(virtualNodeInfoReadService)

    @Test
    fun `initialises the flow checkpoint from the avro checkpoint`() {
        val inputContext = buildFlowEventContext(mock(), inputEventPayload = startFlow, flowId = flowId)
        handler.preProcess(inputContext)
        verify(inputContext.checkpoint).initFlowState(startFlow.startContext)
        verify(inputContext.checkpoint).waitingFor = WaitingFor(WaitingForStartFlow)
    }

    @Test
    fun `when in a retry still set the flow context`() {
        val inputContext = buildFlowEventContext(mock(), inputEventPayload = startFlow, flowId = flowId)
        whenever(inputContext.checkpoint.inRetryState).thenReturn(true)

        handler.preProcess(inputContext)
        verify(inputContext.checkpoint).initFlowState(startFlow.startContext)
        verify(inputContext.checkpoint).waitingFor = WaitingFor(WaitingForStartFlow)
    }
}