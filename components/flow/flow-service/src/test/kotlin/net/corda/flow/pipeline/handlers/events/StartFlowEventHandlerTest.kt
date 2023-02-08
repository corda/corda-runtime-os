package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.handlers.waiting.WaitingForStartFlow
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StartFlowEventHandlerTest {

    private val startFlow = StartFlow(FlowStartContext(), "start args")
    private val flowId = "flow id"
    private val checkpointInitializer = mock<CheckpointInitializer>()
    private val handler = StartFlowEventHandler(checkpointInitializer)

    @Test
    fun `initialises the flow checkpoint from the avro checkpoint`() {
        val context = buildFlowEventContext(mock(), inputEventPayload = startFlow, flowId = flowId)
        handler.preProcess(context)
        verify(checkpointInitializer).initialize(context.checkpoint, startFlow.startContext, WaitingFor(WaitingForStartFlow))
    }

    @Test
    fun `when in a retry still set the flow context`() {
        val context = buildFlowEventContext(mock(), inputEventPayload = startFlow, flowId = flowId)
        whenever(context.checkpoint.inRetryState).thenReturn(true)

        handler.preProcess(context)
        verify(checkpointInitializer).initialize(context.checkpoint, startFlow.startContext, WaitingFor(WaitingForStartFlow))

    }
}