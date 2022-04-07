package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.waiting.SessionData
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ReceiveRequestHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
    }

    private val inputContext: FlowEventContext<Any> = buildFlowEventContext(
        checkpoint = Checkpoint().apply {
            flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group Id"))
        },
        inputEventPayload = Unit
    )

    private val flowSessionManager = mock<FlowSessionManager>()

    private val receiveRequestHandler = ReceiveRequestHandler(flowSessionManager)

    @Test
    fun `Returns an updated WaitingFor of SessionData`() {
        val result = receiveRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.Receive(setOf(SESSION_ID, ANOTHER_SESSION_ID))
        )
        assertEquals(SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID)), result.value)
    }

    @Test
    fun `Creates a Wakeup record if all the sessions have already received events`() {
        whenever(flowSessionManager.hasReceivedEvents(inputContext.checkpoint!!, listOf(SESSION_ID, ANOTHER_SESSION_ID))).thenReturn(true)
        val outputContext = receiveRequestHandler.postProcess(
            inputContext,
            FlowIORequest.Receive(setOf(SESSION_ID, ANOTHER_SESSION_ID))
        )
        assertEquals(Wakeup(), (outputContext.outputRecords.single().value as FlowEvent).payload)
    }

    @Test
    fun `Does not create a Wakeup record if any the sessions have not already received events`() {
        whenever(flowSessionManager.hasReceivedEvents(inputContext.checkpoint!!, listOf(SESSION_ID, ANOTHER_SESSION_ID))).thenReturn(false)

        val outputContext = receiveRequestHandler.postProcess(
            inputContext,
            FlowIORequest.Receive(setOf(SESSION_ID, ANOTHER_SESSION_ID))
        )
        assertEquals(0, outputContext.outputRecords.size)
    }

    @Test
    fun `Does not modify the context when the sessions have not already received events`() {
        whenever(flowSessionManager.hasReceivedEvents(inputContext.checkpoint!!, listOf(SESSION_ID, ANOTHER_SESSION_ID))).thenReturn(false)
        val outputContext = receiveRequestHandler.postProcess(
            inputContext,
            FlowIORequest.Receive(setOf(SESSION_ID, ANOTHER_SESSION_ID))
        )
        assertEquals(inputContext, outputContext)
    }
}