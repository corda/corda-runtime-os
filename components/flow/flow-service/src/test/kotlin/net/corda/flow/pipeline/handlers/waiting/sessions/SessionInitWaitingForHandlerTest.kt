package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.handlers.waiting.SessionInitWaitingForHandler
import net.corda.flow.pipeline.handlers.waiting.WaitingForSessionInit
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SessionInitWaitingForHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"
    }

    private val sessionInitWaitingForHandler = SessionInitWaitingForHandler()
    @Test
    fun `Returns FlowContinuation#Run`() {
        val inputContext = buildFlowEventContext(
            checkpoint = mock(),
            inputEventPayload = mock<SessionEvent>()
        )

        val continuation = sessionInitWaitingForHandler.runOrContinue(inputContext, WaitingForSessionInit(SESSION_ID))

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }
}