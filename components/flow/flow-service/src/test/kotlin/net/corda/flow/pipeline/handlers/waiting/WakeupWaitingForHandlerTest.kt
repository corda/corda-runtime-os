package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WakeupWaitingForHandlerTest {

    @Test
    fun `Returns a FlowContinuation#Run when a wakeup event is received`() {
        val inputContext = buildFlowEventContext(
            checkpoint = mock(),
            inputEventPayload = net.corda.data.flow.event.Wakeup()
        )
        val continuation = WakeupWaitingForHandler().runOrContinue(inputContext, Wakeup())
        assertEquals(FlowContinuation.Run(Unit), continuation)
    }

    @Test
    fun `Returns a FlowContinuation#Continue when a non-wakeup event is received`() {
        val checkpoint = mock<FlowCheckpoint>()
        whenever(checkpoint.flowId).thenReturn("flow id")
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = SessionEvent()
        )
        val continuation = WakeupWaitingForHandler().runOrContinue(inputContext, Wakeup())
        assertEquals(FlowContinuation.Continue, continuation)
    }
}