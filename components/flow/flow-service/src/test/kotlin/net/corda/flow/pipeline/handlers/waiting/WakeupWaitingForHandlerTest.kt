package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.v5.application.flows.exceptions.FlowException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WakeupWaitingForHandlerTest {

    @Test
    fun `Returns a FlowContinuation#Run when an event is received and there is not pending platform error`() {
        val inputContext = buildFlowEventContext(
            checkpoint = mock(),
            inputEventPayload = SessionEvent()
        )
        val continuation = WakeupWaitingForHandler().runOrContinue(inputContext, Wakeup())
        assertEquals(FlowContinuation.Run(Unit), continuation)
    }

    @Test
    fun `Returns a FlowContinuation#Error when there is a pending platform error`() {
        val platformError = ExceptionEnvelope("a","b")
        val inputContext = buildFlowEventContext(
            checkpoint = mock(),
            inputEventPayload = SessionEvent()
        )

        whenever(inputContext.checkpoint.pendingPlatformError).thenReturn(platformError)

        val continuation = WakeupWaitingForHandler().runOrContinue(inputContext, Wakeup()) as FlowContinuation.Error

        assertThat(continuation.exception).isInstanceOf(FlowException::class.java)
        assertThat(continuation.exception.message).isEqualTo("Type='a' Message='b'")
    }
}