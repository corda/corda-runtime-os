package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class SubFlowFinishedRequestHandlerTest {

    private val flowStackItem = FlowStackItem()
    private val ioRequest = FlowIORequest.SubFlowFinished(flowStackItem)
    private val testContext = RequestHandlerTestContext(Any())
    private val handler = SubFlowFinishedRequestHandler(testContext.recordFactory)

    @Test
    fun `Updates the waiting for to nothing`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertThat(waitingFor.value is Wakeup).isTrue
    }

    @Test
    fun `post processing publishes wakeup event`() {
        val eventRecord = Record("","", FlowEvent())

        whenever(testContext
            .recordFactory
            .createFlowEventRecord(eq(testContext.flowId), argThat<net.corda.data.flow.event.Wakeup> { true } )
        ).thenReturn(eventRecord)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).containsOnly(eventRecord)
    }
}