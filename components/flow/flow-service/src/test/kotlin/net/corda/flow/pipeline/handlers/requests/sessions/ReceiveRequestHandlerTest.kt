package net.corda.flow.pipeline.handlers.requests.sessions


import net.corda.data.flow.state.waiting.SessionData
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ReceiveRequestHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
    }

    private val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = mock(), inputEventPayload = Unit)

    private val receiveRequestHandler = ReceiveRequestHandler()

    @Test
    fun `Returns an updated WaitingFor of SessionData`() {
        val result = receiveRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.Receive(setOf(SESSION_ID, ANOTHER_SESSION_ID))
        )
        assertEquals(SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID)), result.value)
    }

    @Test
    fun `Does not modify the context`() {
        val outputContext = receiveRequestHandler.postProcess(
            inputContext,
            FlowIORequest.Receive(setOf(SESSION_ID, ANOTHER_SESSION_ID))
        )
        assertEquals(inputContext, outputContext)
    }
}