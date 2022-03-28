package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.session.manager.SessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class CloseSessionsRequestHandlerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
    }

    private val sessionState = SessionState.newBuilder()
        .setSessionId(SESSION_ID)
        .setSessionStartTime(Instant.now())
        .setLastReceivedMessageTime(Instant.now())
        .setLastSentMessageTime(Instant.now())
        .setCounterpartyIdentity(HoldingIdentity("Alice", "group1"))
        .setIsInitiator(true)
        .setSendAck(true)
        .setReceivedEventsState(SessionProcessState(0, emptyList()))
        .setSendEventsState(SessionProcessState(0, emptyList()))
        .setStatus(SessionStateType.CONFIRMED)
        .build()

    private val anotherSessionState = SessionState.newBuilder(sessionState).setSessionId(ANOTHER_SESSION_ID).build()

    private val sessionManager = mock<SessionManager>()

    private val argumentCaptor = argumentCaptor<SessionEvent>()

    private val closeSessionsRequestHandler = CloseSessionsRequestHandler(sessionManager)

    @Test
    fun `Returns an updated WaitingFor of SessionConfirmation (Close)`() {
        val inputContext: FlowEventContext<Any> = FlowEventContext(
            checkpoint = Checkpoint(),
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )
        val sessions = setOf(SESSION_ID, ANOTHER_SESSION_ID)
        val result = closeSessionsRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.CloseSessions(sessions)
        )

        assertEquals(SessionConfirmation(sessions.toList(), SessionConfirmationType.CLOSE), result.value)
    }

    @Test
    fun `Updates the checkpoint's sessions with session close events`() {
        whenever(sessionManager.processMessageToSend(any(), any(), any(), any())).then {
            val sessionState = it.getArgument(1) as SessionState
            SessionState.newBuilder(sessionState)
                .setSendEventsState(
                    SessionProcessState(
                        1,
                        sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                    )
                )
                .build()
        }

        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = listOf(sessionState, anotherSessionState)
        }
        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            fiber = checkpoint.fiber
            sessions = checkpoint.sessions
        }
        val inputContext: FlowEventContext<Any> = FlowEventContext(
            checkpoint = checkpointCopy,
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )
        val outputContext = closeSessionsRequestHandler.postProcess(
            inputContext,
            FlowIORequest.CloseSessions(setOf(SESSION_ID, ANOTHER_SESSION_ID))
        )

        val sessionOutput1 = outputContext.checkpoint?.sessions?.get(0)
        val sessionOutput2 = outputContext.checkpoint?.sessions?.get(1)
        assertNotEquals(checkpoint, outputContext.checkpoint)
        assertNotEquals(sessionState, sessionOutput1)
        assertNotEquals(anotherSessionState, sessionOutput2)
        assertNotEquals(sessionOutput1, sessionOutput2)
        verify(sessionManager, times(2)).processMessageToSend(any(), any(), argumentCaptor.capture(), any())
        assertTrue(argumentCaptor.firstValue.payload is SessionClose)
        assertTrue(argumentCaptor.secondValue.payload is SessionClose)
    }

    @Test
    fun `Throws an exception if there is no checkpoint`() {
        val inputContext: FlowEventContext<Any> = FlowEventContext(
            checkpoint = null,
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )
        assertThrows<FlowProcessingException> {
            closeSessionsRequestHandler.postProcess(inputContext, FlowIORequest.CloseSessions(setOf(SESSION_ID, ANOTHER_SESSION_ID)))
        }
    }
}