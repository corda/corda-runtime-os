package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.handlers.waiting.WaitingForStartFlow
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class StartFlowEventHandlerTest {

    private val startFlow = StartFlow(FlowStartContext(), "start args")
    private val flowId = "flow id"
    private val handler = StartFlowEventHandler()

    @Test
    fun `initialises the flow checkpoint from the avro checkpoint`() {
        val inputContext = buildFlowEventContext(mock(), inputEventPayload = startFlow, flowId = flowId)
        handler.preProcess(inputContext)
        verify(inputContext.checkpoint).initFromNew(flowId, startFlow.startContext, WaitingFor(WaitingForStartFlow))
    }
}