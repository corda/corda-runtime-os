package net.corda.flow.pipeline.handlers.waiting.sessions

import java.nio.ByteBuffer
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
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
class SessionDataWaitingForHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val SESSION_ID_2 = "session id 2"
        const val SESSION_ID_3 = "session id 3"
        val DATA = byteArrayOf(1, 1, 1, 1)
        val MORE_DATA = byteArrayOf(2, 2, 2, 2)
        val sessions = listOf(SESSION_ID, SESSION_ID_2)
    }

    private val checkpoint = mock<FlowCheckpoint>()
    private val sessionState = SessionState()
    private val sessionStateTwo = SessionState()
    private val sessionStateThree = SessionState()
    private val flowSessionManager = mock<FlowSessionManager>()
    private val sessionDataWaitingForHandler = SessionDataWaitingForHandler(flowSessionManager)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        sessionState.sessionId = SESSION_ID
        sessionStateTwo.sessionId = SESSION_ID_2
        sessionStateThree.sessionId = SESSION_ID_3

        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState)
        whenever(checkpoint.getSessionState(sessionStateTwo.sessionId)).thenReturn(sessionStateTwo)

        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR)).thenReturn(emptyList())
    }

    @Test
    fun `Receiving a session required session data events returns a FlowContinuation#Run`() {
        whenever(flowSessionManager.getReceivedEvents(checkpoint, sessions))
            .thenReturn(
                listOf(
                    sessionState to SessionEvent().apply {
                        sessionId = SESSION_ID
                        payload = SessionData(ByteBuffer.wrap(DATA))
                        sequenceNum = 1
                    },
                    sessionStateTwo to SessionEvent().apply {
                        sessionId = SESSION_ID_2
                        payload = SessionData(ByteBuffer.wrap(MORE_DATA))
                        sequenceNum = 1
                    }
                )
            )

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(sessions)
        )

        assertEquals(FlowContinuation.Run(mapOf(SESSION_ID to DATA, SESSION_ID_2 to MORE_DATA)), continuation)
    }

    @Test
    fun `All sessions being errored returns a FlowContinuation#Error`() {
        whenever(flowSessionManager.getReceivedEvents(checkpoint, sessions)).thenReturn(emptyList())
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR))
            .thenReturn(listOf(sessionState, sessionStateTwo))

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(sessions)
        )

        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        assertInstanceOf(CordaRuntimeException::class.java, (continuation as FlowContinuation.Error).exception)
    }

    @Test
    fun `All sessions being errored, closing or receiving their session data events returns a FlowContinuation#Error`() {
        val sessions = listOf(SESSION_ID, SESSION_ID_2, SESSION_ID_3)

        whenever(flowSessionManager.getReceivedEvents(checkpoint, sessions))
            .thenReturn(
                listOf(
                    sessionState to SessionEvent().apply {
                        sessionId = SESSION_ID
                        payload = SessionData(ByteBuffer.wrap(DATA))
                        sequenceNum = 1
                    },
                )
            )

        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, listOf(SESSION_ID_2, SESSION_ID_3), SessionStateType.ERROR))
            .thenReturn(listOf(sessionStateTwo))
        whenever(flowSessionManager.getSessionsWithNextMessageClose(checkpoint))
            .thenReturn(listOf(sessionStateThree))

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(sessions)
        )

        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        assertInstanceOf(CordaRuntimeException::class.java, (continuation as FlowContinuation.Error).exception)
    }

    @Test
    fun `When there are still events to receive and some sessions are errored a FlowContinuation#Continue is returned`() {
        whenever(flowSessionManager.getReceivedEvents(checkpoint, sessions)).thenReturn(emptyList())
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR)).thenReturn(listOf(sessionState))

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(sessions)
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `Requiring more session events to be received returns a FlowContinuation#Continue`() {
        whenever(flowSessionManager.getReceivedEvents(checkpoint, sessions))
            .thenReturn(
                listOf(
                    sessionState to SessionEvent().apply {
                        sessionId = SESSION_ID
                        payload = SessionData(ByteBuffer.wrap(DATA))
                        sequenceNum = 1
                    }
                ))

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(sessions)
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `A closing or errored session that has already received a session data event returns a FlowContinuation#Run`() {
        whenever(flowSessionManager.getReceivedEvents(checkpoint, listOf(SESSION_ID)))
            .thenReturn(
                listOf(
                    sessionState to SessionEvent().apply {
                        sessionId = SESSION_ID
                        payload = SessionData(ByteBuffer.wrap(DATA))
                        sequenceNum = 1
                    },
                )
            )

        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, emptyList(), SessionStateType.ERROR))
            .thenReturn(emptyList())
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, emptyList(), SessionStateType.CLOSING))
            .thenReturn(emptyList())

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(listOf(SESSION_ID))
        )

        assertEquals(FlowContinuation.Run(mapOf(SESSION_ID to DATA)), continuation)
        verify(flowSessionManager).getSessionsWithStatus(checkpoint, emptyList(), SessionStateType.ERROR)
        verify(flowSessionManager).getSessionsWithNextMessageClose(checkpoint)
    }

    @Test
    fun `Receiving all required session data events acknowledges the received events`() {
        val receivedEvents = listOf(
            sessionState to SessionEvent().apply {
                sessionId = SESSION_ID
                payload = SessionData(ByteBuffer.wrap(DATA))
                sequenceNum = 1
            },
            sessionStateTwo to SessionEvent().apply {
                sessionId = SESSION_ID_2
                payload = SessionData(ByteBuffer.wrap(MORE_DATA))
                sequenceNum = 1
            }
        )

        whenever(flowSessionManager.getReceivedEvents(checkpoint, sessions)).thenReturn(receivedEvents)

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(sessions)
        )

        verify(flowSessionManager).acknowledgeReceivedEvents(receivedEvents)
    }

    @Test
    fun `Acknowledges the received data events when all sessions are either errored, closing or have received their session data events`() {
        val sessions = listOf(SESSION_ID, SESSION_ID_2, SESSION_ID_3)

        val receivedEvents = listOf(
            sessionState to SessionEvent().apply {
                sessionId = SESSION_ID
                payload = SessionData(ByteBuffer.wrap(DATA))
                sequenceNum = 1
            }
        )

        whenever(flowSessionManager.getReceivedEvents(checkpoint, sessions)).thenReturn(receivedEvents)
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, listOf(SESSION_ID_2, SESSION_ID_3), SessionStateType.ERROR))
            .thenReturn(listOf(sessionStateTwo))
        whenever(flowSessionManager.getSessionsWithNextMessageClose(checkpoint))
            .thenReturn(listOf(sessionStateThree))

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(sessions)
        )

        verify(flowSessionManager).acknowledgeReceivedEvents(receivedEvents)
    }

    @Test
    fun `Does not acknowledge the received data events when there are errored sessions and the remaining sessions have not have received their session data events`() {
        whenever(flowSessionManager.getReceivedEvents(checkpoint, sessions)).thenReturn(emptyList())
        whenever(flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR))
            .thenReturn(listOf(sessionStateTwo))

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(sessions)
        )

        verify(flowSessionManager, never()).acknowledgeReceivedEvents(any())
    }

    @Test
    fun `Receiving a non-session data event throws an exception`() {
        whenever(flowSessionManager.getReceivedEvents(checkpoint, sessions))
            .thenReturn(
                listOf(
                    sessionState to SessionEvent().apply {
                        sessionId = SESSION_ID
                        payload = SessionData(ByteBuffer.wrap(DATA))
                        sequenceNum = 1
                    },
                    sessionStateTwo to SessionEvent().apply {
                        sessionId = SESSION_ID_2
                        payload = SessionClose()
                        sequenceNum = 1
                    }
                ))

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(sessions)
        )
        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        assertInstanceOf(IllegalStateException::class.java, (continuation as FlowContinuation.Error).exception)
    }
}