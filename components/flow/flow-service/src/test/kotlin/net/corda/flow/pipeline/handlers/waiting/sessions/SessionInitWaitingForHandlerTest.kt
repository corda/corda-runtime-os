package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.session.manager.SessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionInitWaitingForHandlerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
    }

    private val sessionManager = mock<SessionManager>()

    private val sessionInitWaitingForHandler = SessionInitWaitingForHandler(sessionManager)

    @Test
    fun `Returns FlowContinuation#Run after receiving next session event`() {
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }
        val sessionEvent = SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionInit()
            sequenceNum = 1
        }

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)

        val inputContext = buildFlowEventContext(
            checkpoint = Checkpoint().apply {
                sessions = listOf(sessionState)
            },
            inputEventPayload = sessionEvent
        )

        val continuation = sessionInitWaitingForHandler.runOrContinue(inputContext, WaitingForSessionInit(SESSION_ID))

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }

    @Test
    fun `Throws an exception if the session being waited for does not exist in the checkpoint`() {
        val inputContext = buildFlowEventContext(
            checkpoint = Checkpoint().apply {
                sessions = emptyList()
            },
            inputEventPayload = Unit
        )
        assertThrows<FlowProcessingException> {
            sessionInitWaitingForHandler.runOrContinue(inputContext, WaitingForSessionInit(SESSION_ID))
        }
    }

    @Test
    fun `Throws an exception if no session event is received`() {
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(null)

        val inputContext = buildFlowEventContext(
            checkpoint = Checkpoint().apply {
                sessions = listOf(sessionState)
            },
            inputEventPayload = Unit
        )
        assertThrows<FlowProcessingException> {
            sessionInitWaitingForHandler.runOrContinue(inputContext, WaitingForSessionInit(SESSION_ID))
        }
    }

    @Test
    fun `Throws an exception if there is no checkpoint`() {
        val inputContext = buildFlowEventContext(checkpoint = null, inputEventPayload = Unit)

        assertThrows<FlowProcessingException> {
            sessionInitWaitingForHandler.runOrContinue(inputContext, WaitingForSessionInit(SESSION_ID))
        }
    }
}