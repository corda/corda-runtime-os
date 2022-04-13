package net.corda.flow

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.state.FlowCheckpoint
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowWaitingForHandlerTestContext<T>(val waitingFor: T) {
    val flowId = "flow id"
    val flowEvent = FlowEvent()
    val flowCheckpoint: FlowCheckpoint = mock()
    val flowEventContext = FlowEventContext(flowCheckpoint, flowEvent, waitingFor, mock(), emptyList())

    init {
        whenever(flowCheckpoint.flowId).thenReturn(flowId)
    }
}