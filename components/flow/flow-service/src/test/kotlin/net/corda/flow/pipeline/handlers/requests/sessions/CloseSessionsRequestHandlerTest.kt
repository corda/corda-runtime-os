package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
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
import java.nio.ByteBuffer

class CloseSessionsRequestHandlerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
        val sessions = listOf(SESSION_ID, ANOTHER_SESSION_ID)
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
        whenever(sendCloseMessages(any(), eq(sessions), any())).thenReturn(listOf(updatedSessionState, anotherUpdatedSessionState))
    }

    private val closeSessionsRequestHandler = CloseSessionsRequestHandler(flowSessionManager)

    @Test
    fun `Returns an updated WaitingFor of SessionConfirmation (Close)`() {
        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = Checkpoint(), inputEventPayload = Unit)
        val result = closeSessionsRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.CloseSessions(sessions.toSet())
        )

        assertEquals(SessionConfirmation(sessions.toList(), SessionConfirmationType.CLOSE), result.value)
    }

    @Test
    fun `Updates the checkpoint's sessions with session close messages to send`() {
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

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = closeSessionsRequestHandler.postProcess(
            inputContext,
            FlowIORequest.CloseSessions(sessions.toSet())
        )

        assertNotEquals(checkpoint, outputContext.checkpoint)
        assertNotEquals(sessionState, outputContext.checkpoint?.sessions?.get(0))
        assertNotEquals(anotherSessionState, outputContext.checkpoint?.sessions?.get(1))
    }

    @Test
    fun `Creates a Wakeup record if all the sessions are already closed or waiting for final acknowledgement`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = listOf(sessionState, anotherSessionState)
        }
        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint, inputEventPayload = Unit)
        whenever(
            flowSessionManager.areAllSessionsInStatuses(
                eq(inputContext.checkpoint!!),
                eq(listOf(SESSION_ID, ANOTHER_SESSION_ID)),
                any()
            )
        ).thenReturn(true)
        val outputContext = closeSessionsRequestHandler.postProcess(
            inputContext,
            FlowIORequest.CloseSessions(sessions.toSet())
        )
        assertEquals(Wakeup(), (outputContext.outputRecords.single().value as FlowEvent).payload)
    }

    @Test
    fun `Does not create a Wakeup record if any of the sessions are not closed or waiting for final acknowledgement`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = listOf(sessionState, anotherSessionState)
        }
        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint, inputEventPayload = Unit)
        whenever(
            flowSessionManager.areAllSessionsInStatuses(
                eq(inputContext.checkpoint!!),
                eq(listOf(SESSION_ID, ANOTHER_SESSION_ID)),
                any()
            )
        ).thenReturn(false)
        val outputContext = closeSessionsRequestHandler.postProcess(
            inputContext,
            FlowIORequest.CloseSessions(sessions.toSet())
        )
        assertEquals(0, outputContext.outputRecords.size)
    }

    @Test
    fun `Throws an exception if there is no checkpoint`() {
        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = null, inputEventPayload = Unit)
        assertThrows<FlowProcessingException> {
            closeSessionsRequestHandler.postProcess(inputContext, FlowIORequest.CloseSessions(sessions.toSet()))
        }
    }
}