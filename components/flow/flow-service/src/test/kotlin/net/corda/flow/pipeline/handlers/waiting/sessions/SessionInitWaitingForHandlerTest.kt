package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionCounterpartyInfoRQ
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.handlers.waiting.SessionInitWaitingForHandler
import net.corda.flow.pipeline.handlers.waiting.WaitingForSessionInit
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionInitWaitingForHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"
    }

    private val checkpoint = mock<FlowCheckpoint>()
    private val sessionState = SessionState()
    private val sessionInitWaitingForHandler = SessionInitWaitingForHandler()

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        sessionState.sessionId = SESSION_ID

        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState)
    }

    @Test
    fun `Returns FlowContinuation#Run `() {
        val sessionEvent = SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionCounterpartyInfoRQ()
            sequenceNum = 1
        }
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = sessionEvent
        )

        val continuation = sessionInitWaitingForHandler.runOrContinue(inputContext, WaitingForSessionInit(SESSION_ID))

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }
}