package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.session.manager.SessionManager
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class SessionConfirmationWaitingForHandlerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
    }

    private val sessionManager = mock<SessionManager>()

    private val sessionConfirmationWaitingForHandler = SessionConfirmationWaitingForHandler(sessionManager)

    @Test
    fun `Receiving a session ack payload for the session being waited for while waiting for a session confirmation (Initiate) returns a FlowContinuation#Run`() {
        val inputContext = FlowEventContext(
            checkpoint = Checkpoint(),
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = SessionEvent().apply {
                sessionId = SESSION_ID
                payload = SessionAck()
            },
            outputRecords = emptyList()
        )
        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(listOf(SESSION_ID), SessionConfirmationType.INITIATE)
        )

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }

    @Test
    fun `Receiving a session ack payload for the wrong session being waited for while waiting for a session confirmation (Initiate) returns a FlowContinuation#Continue`() {
        val inputContext = FlowEventContext(
            checkpoint = Checkpoint(),
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = SessionEvent().apply {
                sessionId = ANOTHER_SESSION_ID
                payload = SessionAck()
            },
            outputRecords = emptyList()
        )
        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(listOf(SESSION_ID), SessionConfirmationType.INITIATE)
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `Receiving a non-session event while waiting for a session confirmation (Initiate) returns a FlowContinuation#Continue`() {
        val inputContext = FlowEventContext(
            checkpoint = Checkpoint(),
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Wakeup(),
            outputRecords = emptyList()
        )
        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(listOf(SESSION_ID), SessionConfirmationType.INITIATE)
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `Receiving a non-session ack payload while waiting for a session confirmation (Initiate) returns a FlowContinuation#Continue`() {
        val inputContext = FlowEventContext(
            checkpoint = Checkpoint(),
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = SessionEvent().apply {
                sessionId = SESSION_ID
                payload = SessionData()
            },
            outputRecords = emptyList()
        )
        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(listOf(SESSION_ID), SessionConfirmationType.INITIATE)
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `Receiving all required session closes while waiting for a session confirmation (Close) returns a FlowContinuation#Run`() {
        val sessions = listOf(SESSION_ID, ANOTHER_SESSION_ID)
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }
        val anotherSessionState = SessionState().apply {
            sessionId = ANOTHER_SESSION_ID
        }
        val sessionEvent = SessionEvent().apply {
            payload = SessionClose()
            sequenceNum = 1
        }
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(sessionEvent)

        val inputContext = FlowEventContext(
            checkpoint = Checkpoint().apply {
                this.sessions = listOf(sessionState, anotherSessionState)
            },
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(sessions, SessionConfirmationType.CLOSE)
        )

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }

    @Test
    fun `Receiving all required session closes while waiting for a session confirmation (Close) acknowledges the received events`() {
        val sessions = listOf(SESSION_ID, ANOTHER_SESSION_ID)
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }
        val anotherSessionState = SessionState().apply {
            sessionId = ANOTHER_SESSION_ID
        }
        val sessionEvent = SessionEvent().apply {
            payload = SessionClose()
            sequenceNum = 1
        }
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(sessionEvent)

        val inputContext = FlowEventContext(
            checkpoint = Checkpoint().apply {
                this.sessions = listOf(sessionState, anotherSessionState)
            },
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        sessionConfirmationWaitingForHandler.runOrContinue(inputContext, SessionConfirmation(sessions, SessionConfirmationType.CLOSE))

        verify(sessionManager, times(2)).acknowledgeReceivedEvent(any(), eq(1))
    }

    @Test
    fun `Receiving all session events which contains a non-session close event while waiting for a session confirmation (Close) returns a FlowContinuation#Error`() {
        val sessions = listOf(SESSION_ID, ANOTHER_SESSION_ID)
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }
        val anotherSessionState = SessionState().apply {
            sessionId = ANOTHER_SESSION_ID
        }
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(SessionEvent().apply {
            payload = SessionClose()
            sequenceNum = 1
        })
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(SessionEvent().apply {
            payload = SessionData()
            sequenceNum = 1
        })

        val inputContext = FlowEventContext(
            checkpoint = Checkpoint().apply {
                this.sessions = listOf(sessionState, anotherSessionState)
            },
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(sessions, SessionConfirmationType.CLOSE)
        )

        assertTrue(continuation is FlowContinuation.Error)
        assertTrue((continuation as FlowContinuation.Error).exception is CordaRuntimeException)
    }

    @Test
    fun `Requiring more session events to be received while waiting for a session confirmation (Close) returns a FlowContinuation#Continue`() {
        val sessions = listOf(SESSION_ID, ANOTHER_SESSION_ID)
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }
        val anotherSessionState = SessionState().apply {
            sessionId = ANOTHER_SESSION_ID
        }
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(SessionEvent().apply {
            payload = SessionClose()
            sequenceNum = 1
        })
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(null)

        val inputContext = FlowEventContext(
            checkpoint = Checkpoint().apply {
                this.sessions = listOf(sessionState, anotherSessionState)
            },
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(sessions, SessionConfirmationType.CLOSE)
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `Throws an exception if there is no checkpoint`() {
        val inputContext = FlowEventContext(
            checkpoint = null,
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        assertThrows<FlowProcessingException> {
            sessionConfirmationWaitingForHandler.runOrContinue(
                inputContext,
                SessionConfirmation(listOf(SESSION_ID, ANOTHER_SESSION_ID), SessionConfirmationType.CLOSE)
            )
        }
    }
}