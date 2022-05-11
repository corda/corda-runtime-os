package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.FlowStackItem
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SubFlowFailedRequestHandlerTest {

    private val flowError = Exception()
    private val flowStackItem = FlowStackItem()
    private val ioRequest = FlowIORequest.SubFlowFailed(flowError, flowStackItem)
    private val handler = SubFlowFailedRequestHandler()
    private val testContext = RequestHandlerTestContext(Any())

    @Test
    fun `Updates the waiting for to nothing`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertThat(waitingFor.value).isNull()
    }
}

