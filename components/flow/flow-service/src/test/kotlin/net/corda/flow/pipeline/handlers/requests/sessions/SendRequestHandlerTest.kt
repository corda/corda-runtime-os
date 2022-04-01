package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.test.utils.buildFlowEventContext
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
import java.time.Instant

class SendRequestHandlerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        val PAYLOAD = byteArrayOf(1, 1, 1, 1)

        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
    }

    private val sessionManager = mock<SessionManager>().apply {
        whenever(processMessageToSend(any(), any(), any(), any())).then {
            val sessionState = it.getArgument(1) as SessionState
            SessionState.newBuilder(it.getArgument(1) as SessionState)
                .setSendEventsState(
                    SessionProcessState(
                        1,
                        sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                    )
                )
                .build()
        }
    }

    private val argumentCaptor = argumentCaptor<SessionEvent>()

    private val sendRequestHandler = SendRequestHandler(sessionManager)

    @Test
    fun `Returns an updated WaitingFor of SessionData`() {
        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = Checkpoint(), inputEventPayload = Unit)
        val result = sendRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.Send(mapOf(SESSION_ID to PAYLOAD, ANOTHER_SESSION_ID to PAYLOAD))
        )
        assertEquals(Wakeup(), result.value)
    }

    @Test
    fun `Updates the sessions being sent to by queueing session data messages to send`() {
        val sessionState = SessionState.newBuilder()
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

        val anotherSessionState = SessionState.newBuilder(sessionState).setSessionId(ANOTHER_SESSION_ID).build()

        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }

        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            sessions = checkpoint.sessions
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = sendRequestHandler.postProcess(
            inputContext,
            FlowIORequest.Send(mapOf(SESSION_ID to PAYLOAD, ANOTHER_SESSION_ID to PAYLOAD))
        )
        val sessionOutput1 = outputContext.checkpoint?.sessions?.get(0)
        val sessionOutput2 = outputContext.checkpoint?.sessions?.get(1)
        assertNotEquals(checkpoint, outputContext.checkpoint)
        assertNotEquals(sessionState, sessionOutput1)
        assertNotEquals(anotherSessionState, sessionOutput2)
        assertNotEquals(sessionOutput1, sessionOutput2)
        assertEquals(1, sessionOutput1?.sendEventsState?.undeliveredMessages?.size)
        assertEquals(1, sessionOutput2?.sendEventsState?.undeliveredMessages?.size)
        verify(sessionManager, times(2)).processMessageToSend(any(), any(), argumentCaptor.capture(), any())
        assertTrue(argumentCaptor.firstValue.payload is SessionData)
        assertTrue(argumentCaptor.secondValue.payload is SessionData)
    }

    @Test
    fun `Adds a wakeup event to the output records`() {
        val sessionState = SessionState.newBuilder()
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

        val anotherSessionState = SessionState.newBuilder(sessionState).setSessionId(ANOTHER_SESSION_ID).build()

        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = Unit)

        val outputContext = sendRequestHandler.postProcess(
            inputContext,
            FlowIORequest.Send(mapOf(SESSION_ID to PAYLOAD, ANOTHER_SESSION_ID to PAYLOAD))
        )

        assertEquals(1, outputContext.outputRecords.size)
        assertEquals(net.corda.data.flow.event.Wakeup(), (outputContext.outputRecords.single().value as FlowEvent).payload)
    }

    @Test
    fun `Throws an exception if there is no checkpoint`() {
        val inputContext = buildFlowEventContext<Any>(checkpoint = null, inputEventPayload = Unit)
        assertThrows<FlowProcessingException> {
            sendRequestHandler.postProcess(inputContext, FlowIORequest.Send(mapOf(SESSION_ID to PAYLOAD, ANOTHER_SESSION_ID to PAYLOAD)))
        }
    }
}