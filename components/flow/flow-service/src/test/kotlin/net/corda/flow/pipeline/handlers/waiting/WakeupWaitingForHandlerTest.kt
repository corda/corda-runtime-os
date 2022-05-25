package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class WakeupWaitingForHandlerTest {

    @Test
    fun `Returns a FlowContinuation#Run when an event is received`() {
        val inputContext = buildFlowEventContext(
            checkpoint = mock(),
            inputEventPayload = SessionEvent()
        )
        val continuation = WakeupWaitingForHandler().runOrContinue(inputContext, Wakeup())
        assertEquals(FlowContinuation.Run(Unit), continuation)
    }
}