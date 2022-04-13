package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowFinishedRequestHandlerTest {

    private val testContext = RequestHandlerTestContext(Any())
    private val flowResult = "ok"
    private val ioRequest = FlowIORequest.FlowFinished(flowResult)
    private val flowStatus = FlowStatus()
    private val handler = FlowFinishedRequestHandler(testContext.flowMessageFactory,testContext.recordFactory)

    @Test
    fun `Updates the waiting for to nothing`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertThat(waitingFor.value).isNull()
    }

    @Test
    fun `post processing marks context as deleted`() {
        handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowCheckpoint).markDeleted()
    }

    @Test
    fun `post processing publishes status update`() {
        val record = Record("",FlowKey(), FlowStatus())

        whenever(testContext.flowMessageFactory.createFlowCompleteStatusMessage(
            testContext.flowCheckpoint,
            flowResult
        )).thenReturn(flowStatus)

        whenever(testContext.recordFactory.createFlowStatusRecord(flowStatus)).thenReturn(record)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).containsOnly(record)
    }
}