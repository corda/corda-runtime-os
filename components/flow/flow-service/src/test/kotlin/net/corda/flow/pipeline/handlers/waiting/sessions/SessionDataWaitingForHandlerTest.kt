package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class SessionDataWaitingForHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        val DATA = byteArrayOf(1, 1, 1, 1)
        val MORE_DATA = byteArrayOf(2, 2, 2, 2)
    }

    private val checkpoint = mock<FlowCheckpoint>()
    private val sessionState = SessionState()
    private val anotherSessionState = SessionState()
    private val sessionManager = mock<SessionManager>()
    private val sessionDataWaitingForHandler = SessionDataWaitingForHandler(sessionManager)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        sessionState.sessionId = SESSION_ID
        anotherSessionState.sessionId = ANOTHER_SESSION_ID

        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState)
        whenever(checkpoint.getSessionState(anotherSessionState.sessionId)).thenReturn(anotherSessionState)
    }

    @Test
    fun `Receiving all required session data events returns a FlowContinuation#Run`() {
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData(ByteBuffer.wrap(DATA))
            sequenceNum = 1
        })
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(SessionEvent().apply {
            sessionId = ANOTHER_SESSION_ID
            payload = SessionData(ByteBuffer.wrap(MORE_DATA))
            sequenceNum = 1
        })

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID))
        )

        assertEquals(FlowContinuation.Run(mapOf(SESSION_ID to DATA, ANOTHER_SESSION_ID to MORE_DATA)), continuation)
    }

    @Test
    fun `Receiving all required session data events acknowledges the received events`() {
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData(ByteBuffer.wrap(DATA))
            sequenceNum = 1
        })
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(SessionEvent().apply {
            sessionId = ANOTHER_SESSION_ID
            payload = SessionData(ByteBuffer.wrap(MORE_DATA))
            sequenceNum = 1
        })

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID))
        )

        verify(sessionManager, times(2)).acknowledgeReceivedEvent(any(), eq(1))
    }

    @Test
    fun `Requiring more session events to be received returns a FlowContinuation#Continue`() {
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData(ByteBuffer.wrap(DATA))
            sequenceNum = 1
        })

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID))
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `Receiving a non-session data event throws an exception`() {
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData(ByteBuffer.wrap(DATA))
            sequenceNum = 1
        })
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(SessionEvent().apply {
            sessionId = ANOTHER_SESSION_ID
            payload = SessionClose()
            sequenceNum = 1
        })

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = Unit
        )

        assertThrows<FlowProcessingException> {
            sessionDataWaitingForHandler.runOrContinue(
                inputContext,
                net.corda.data.flow.state.waiting.SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID))
            )
        }
    }
}