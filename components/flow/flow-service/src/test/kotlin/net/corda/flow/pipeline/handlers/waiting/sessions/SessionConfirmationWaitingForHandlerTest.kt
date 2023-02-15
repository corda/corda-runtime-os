package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class SessionConfirmationWaitingForHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        val sessions = listOf(SESSION_ID, ANOTHER_SESSION_ID)
    }

    private val checkpoint = mock<FlowCheckpoint>()
    private val sessionState = SessionState()
    private val anotherSessionState = SessionState()
    private val flowSessionManager = mock<FlowSessionManager>()
    private val sessionConfirmationWaitingForHandler = SessionConfirmationWaitingForHandler(flowSessionManager)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        sessionState.sessionId = SESSION_ID
        anotherSessionState.sessionId = ANOTHER_SESSION_ID

        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState)
        whenever(checkpoint.getSessionState(anotherSessionState.sessionId)).thenReturn(anotherSessionState)

        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR)).thenReturn(emptyList())
    }

    @Test
    fun `When the session being waited for is confirmed or closing while waiting for a session confirmation (Initiate) returns a FlowContinuation#Run`() {
        whenever(flowSessionManager.doAllSessionsHaveStatusIn(checkpoint, listOf(SESSION_ID), listOf(SessionStateType.CONFIRMED, SessionStateType.CLOSING))).thenReturn(true)
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = SessionEvent().apply {
                sessionId = SESSION_ID
                payload = SessionAck()
            }
        )
        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(listOf(SESSION_ID), SessionConfirmationType.INITIATE)
        )

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }

    @Test
    fun `When the session being waited for is not-confirmed while waiting for a session confirmation (Initiate) returns a FlowContinuation#Continue`() {
        whenever(flowSessionManager.doAllSessionsHaveStatus(checkpoint, listOf(SESSION_ID), SessionStateType.CONFIRMED)).thenReturn(false)
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = SessionEvent().apply {
                sessionId = ANOTHER_SESSION_ID
                payload = SessionAck()
            }
        )
        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(listOf(SESSION_ID), SessionConfirmationType.INITIATE)
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `When the session being waited for is errored while waiting for a session confirmation (Initiate) returns a FlowContinuation#Error`() {
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = SessionEvent().apply {
                sessionId = SESSION_ID
                payload = SessionError()
            }
        )

        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, listOf(SESSION_ID), SessionStateType.ERROR))
            .thenReturn(listOf(sessionState))

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(listOf(SESSION_ID), SessionConfirmationType.INITIATE)
        )

        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        assertInstanceOf(CordaRuntimeException::class.java, (continuation as FlowContinuation.Error).exception)
    }

    @Test
    fun `Receiving a non-session event while waiting for a session confirmation (Initiate) returns a FlowContinuation#Continue`() {
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = Wakeup())
        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(listOf(SESSION_ID), SessionConfirmationType.INITIATE)
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `Returns a FlowContinuation#Run when all sessions are closed while waiting for a session confirmation (Close) returns a FlowContinuation#Run`() {
        val sessionEvent = SessionEvent().apply {
            payload = SessionClose()
            sequenceNum = 1
        }

        whenever(flowSessionManager.doAllSessionsHaveStatus(checkpoint, sessions, SessionStateType.CLOSED)).thenReturn(true)
        whenever(
            flowSessionManager.getReceivedEvents(
                checkpoint,
                sessions
            )
        ).thenReturn(
            listOf(
                sessionState to sessionEvent,
                anotherSessionState to sessionEvent
            )
        )

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(sessions, SessionConfirmationType.CLOSE)
        )

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }

    @Test
    fun `Acknowledges the received events when all sessions are closed while waiting for a session confirmation (Close)`() {
        val sessionEvent = SessionEvent().apply {
            payload = SessionClose()
            sequenceNum = 1
        }

        val receivedEvents = listOf(
            sessionState to sessionEvent,
            anotherSessionState to sessionEvent
        )

        whenever(flowSessionManager.doAllSessionsHaveStatus(checkpoint, sessions, SessionStateType.CLOSED)).thenReturn(true)
        whenever(
            flowSessionManager.getReceivedEvents(
                checkpoint,
                sessions
            )
        ).thenReturn(receivedEvents)

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        sessionConfirmationWaitingForHandler.runOrContinue(inputContext, SessionConfirmation(sessions, SessionConfirmationType.CLOSE))

        verify(flowSessionManager).acknowledgeReceivedEvents(receivedEvents)
    }

    @Test
    fun `Returns a FlowContinuation#Error when all sessions are errored while waiting for a session confirmation (Close)`() {
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR))
            .thenReturn(listOf(sessionState, anotherSessionState))
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, emptyList(), SessionStateType.CLOSED))
            .thenReturn(emptyList())

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(sessions, SessionConfirmationType.CLOSE)
        )

        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        assertInstanceOf(CordaRuntimeException::class.java, (continuation as FlowContinuation.Error).exception)
    }

    @Test
    fun `Returns a FlowContinuation#Error when all sessions are either errored or closed while waiting for a session confirmation (Close)`() {
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR))
            .thenReturn(listOf(sessionState))
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, listOf(ANOTHER_SESSION_ID), SessionStateType.CLOSED))
            .thenReturn(listOf(anotherSessionState))

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(sessions, SessionConfirmationType.CLOSE)
        )

        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        assertInstanceOf(CordaRuntimeException::class.java, (continuation as FlowContinuation.Error).exception)
    }

    @Test
    fun `Returns a FlowContinuation#Continue when there are errored sessions and the remaining sessions are not closed while waiting for a session confirmation (Close)`() {
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR))
            .thenReturn(listOf(sessionState))
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, listOf(ANOTHER_SESSION_ID), SessionStateType.CLOSED))
            .thenReturn(emptyList())

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(sessions, SessionConfirmationType.CLOSE)
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `Returns a FlowContinuation#Error when receiving a non-session close event when all sessions are closed while waiting for a session confirmation (Close)`() {
        whenever(flowSessionManager.doAllSessionsHaveStatus(checkpoint, sessions, SessionStateType.CLOSED)).thenReturn(true)
        whenever(
            flowSessionManager.getReceivedEvents(
                checkpoint,
                sessions
            )
        ).thenReturn(
            listOf(
                sessionState to SessionEvent().apply {
                    payload = SessionClose()
                    sequenceNum = 1
                },
                anotherSessionState to SessionEvent().apply {
                    payload = SessionData()
                    sequenceNum = 1
                }
            )
        )

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(sessions, SessionConfirmationType.CLOSE)
        )

        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        assertInstanceOf(CordaRuntimeException::class.java, (continuation as FlowContinuation.Error).exception)
    }

    @Test
    fun `Acknowledges the received close events when all sessions are either errored or closed while waiting for a session confirmation (Close)`() {
        val sessionEvent = SessionEvent().apply {
            payload = SessionClose()
            sequenceNum = 1
        }

        val receivedEvents = listOf(sessionState to sessionEvent)

        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR))
            .thenReturn(listOf(sessionState))
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, listOf(ANOTHER_SESSION_ID), SessionStateType.CLOSED))
            .thenReturn(listOf(anotherSessionState))

        whenever(
            flowSessionManager.getReceivedEvents(
                checkpoint,
                sessions
            )
        ).thenReturn(receivedEvents)

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        sessionConfirmationWaitingForHandler.runOrContinue(inputContext, SessionConfirmation(sessions, SessionConfirmationType.CLOSE))

        verify(flowSessionManager).acknowledgeReceivedEvents(receivedEvents)
    }

    @Test
    fun `Does not acknowledge any received close events when there are errored sessions and the remaining sessions are not closed while waiting for a session confirmation (Close)`() {
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR))
            .thenReturn(listOf(sessionState))
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, listOf(ANOTHER_SESSION_ID), SessionStateType.CLOSED))
            .thenReturn(emptyList())

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        sessionConfirmationWaitingForHandler.runOrContinue(inputContext, SessionConfirmation(sessions, SessionConfirmationType.CLOSE))

        verify(flowSessionManager, never()).acknowledgeReceivedEvents(any())
    }

    @Test
    fun `Returns a FlowContinuation#Continue if any sessions are not closed while waiting for a session confirmation (Close)`() {
        whenever(flowSessionManager.doAllSessionsHaveStatus(checkpoint, sessions, SessionStateType.CLOSED)).thenReturn(false)

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(
            inputContext,
            SessionConfirmation(sessions, SessionConfirmationType.CLOSE)
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }
}