package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.session.manager.SessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionInitWaitingForHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"
    }

    private val checkpoint = mock<FlowCheckpoint>()
    private val sessionState = SessionState()
    private val sessionManager = mock<SessionManager>()
    private val sessionInitWaitingForHandler = SessionInitWaitingForHandler(sessionManager)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        sessionState.sessionId = SESSION_ID

        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState)
    }

    @Test
    fun `Returns FlowContinuation#Run after receiving next session event`() {
        val sessionEvent = SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionInit()
            sequenceNum = 1
        }

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = sessionEvent
        )

        val continuation = sessionInitWaitingForHandler.runOrContinue(inputContext, WaitingForSessionInit(SESSION_ID))

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }

    @Test
    fun `Throws an exception if the session being waited for does not exist in the checkpoint`() {
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )
        assertThrows<FlowProcessingException> {
            sessionInitWaitingForHandler.runOrContinue(inputContext, WaitingForSessionInit(SESSION_ID))
        }
    }

    @Test
    fun `Throws an exception if no session event is received`() {
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(null)

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )
        assertThrows<FlowProcessingException> {
            sessionInitWaitingForHandler.runOrContinue(inputContext, WaitingForSessionInit(SESSION_ID))
        }
    }
}