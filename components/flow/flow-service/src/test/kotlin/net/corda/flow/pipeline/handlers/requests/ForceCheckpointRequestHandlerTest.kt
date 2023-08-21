package net.corda.flow.pipeline.handlers.requests

import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForceCheckpointRequestHandlerTest {

    private val handler = ForceCheckpointRequestHandler()
    private val testContext = RequestHandlerTestContext(Any())

    @Test
    fun `Updates the waiting for to Wakeup`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, FlowIORequest.ForceCheckpoint)
        assertTrue(waitingFor.value is net.corda.data.flow.state.waiting.Wakeup)
    }

    @Test
    fun `Request handler returns input context unchanged`() {
        val outputContext = handler.postProcess(testContext.flowEventContext, FlowIORequest.ForceCheckpoint)
        assertEquals(testContext.flowEventContext, outputContext)
    }
}