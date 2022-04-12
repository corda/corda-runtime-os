package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionData
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.test.flow.util.buildSessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SendAndReceiveRequestHandlerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        val PAYLOAD = byteArrayOf(1, 1, 1, 1)

        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
        val sessionToPayload = mapOf(SESSION_ID to PAYLOAD, ANOTHER_SESSION_ID to PAYLOAD)
    }

    private val sessionState = buildSessionState(
        SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf(), sessionId = SESSION_ID
    )

    private val anotherSessionState = buildSessionState(
        SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf(), sessionId = ANOTHER_SESSION_ID
    )

    private val updatedSessionState = buildSessionState(
        SessionStateType.CONFIRMED, 0, mutableListOf(), 1, mutableListOf(), sessionId = SESSION_ID
    )

    private val anotherUpdatedSessionState = buildSessionState(
        SessionStateType.CONFIRMED, 0, mutableListOf(), 1, mutableListOf(), sessionId = ANOTHER_SESSION_ID
    )

    private val flowSessionManager = mock<FlowSessionManager>().apply {
        whenever(sendDataMessages(any(), eq(sessionToPayload), any())).thenReturn(listOf(updatedSessionState, anotherUpdatedSessionState))
    }

    private val sendAndReceiveRequestHandler = SendAndReceiveRequestHandler(flowSessionManager)

    @Test
    fun `Returns an updated WaitingFor of SessionData`() {
        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = Checkpoint(), inputEventPayload = Unit)
        val result = sendAndReceiveRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.SendAndReceive(sessionToPayload)
        )
        assertEquals(SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID)), result.value)
    }

    @Test
    fun `Updates the checkpoint's sessions with session data messages to send`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }

        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            sessions = checkpoint.sessions
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = sendAndReceiveRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SendAndReceive(sessionToPayload)
        )
        val sessionOutput1 = outputContext.checkpoint?.sessions?.get(0)
        val sessionOutput2 = outputContext.checkpoint?.sessions?.get(1)
        assertNotEquals(checkpoint, outputContext.checkpoint)
        assertNotEquals(sessionState, sessionOutput1)
        assertNotEquals(anotherSessionState, sessionOutput2)
    }

    @Test
    fun `Creates a Wakeup record if all the sessions have already received events`() {
        val inputContext: FlowEventContext<Any> = buildFlowEventContext(
            checkpoint = Checkpoint().apply { flowKey = FLOW_KEY },
            inputEventPayload = Unit
        )

        whenever(flowSessionManager.hasReceivedEvents(inputContext.checkpoint!!, listOf(SESSION_ID, ANOTHER_SESSION_ID))).thenReturn(true)

        val outputContext = sendAndReceiveRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SendAndReceive(sessionToPayload)
        )
        assertEquals(Wakeup(), (outputContext.outputRecords.single().value as FlowEvent).payload)
    }

    @Test
    fun `Does not create a Wakeup record if any the sessions have not already received events`() {
        val inputContext: FlowEventContext<Any> = buildFlowEventContext(
            checkpoint = Checkpoint().apply { flowKey = FLOW_KEY },
            inputEventPayload = Unit
        )

        whenever(flowSessionManager.hasReceivedEvents(inputContext.checkpoint!!, listOf(SESSION_ID, ANOTHER_SESSION_ID))).thenReturn(false)

        val outputContext = sendAndReceiveRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SendAndReceive(sessionToPayload)
        )
        assertEquals(0, outputContext.outputRecords.size)
    }

    @Test
    fun `Throws an exception if there is no checkpoint`() {
        val inputContext = buildFlowEventContext<Any>(checkpoint = null, inputEventPayload = Unit)
        assertThrows<FlowProcessingException> {
            sendAndReceiveRequestHandler.postProcess(
                inputContext,
                FlowIORequest.SendAndReceive(sessionToPayload)
            )
        }
    }
}