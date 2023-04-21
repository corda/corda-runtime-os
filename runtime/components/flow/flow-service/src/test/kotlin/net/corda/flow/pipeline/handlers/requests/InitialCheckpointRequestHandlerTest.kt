package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class InitialCheckpointRequestHandlerTest {

    private val testContext = RequestHandlerTestContext(Any())
    private val ioRequest = FlowIORequest.InitialCheckpoint
    private val flowStatus = FlowStatus()
    private val handler = InitialCheckpointRequestHandler(testContext.flowMessageFactory, testContext.flowRecordFactory)

    @Test
    fun `Updates the waiting for to nothing`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertThat(waitingFor.value).isInstanceOf(Wakeup::class.java)
    }

    @Test
    fun `post processing publishes status update and wakeup event`() {
        val statusRecord = Record("",FlowKey(), FlowStatus())
        val eventRecord = Record("","", FlowEvent())

        whenever(
            testContext.flowMessageFactory.createFlowStartedStatusMessage(testContext.flowCheckpoint)
        ).thenReturn(flowStatus)

        whenever(testContext.flowRecordFactory.createFlowStatusRecord(flowStatus)).thenReturn(statusRecord)
        whenever(testContext
            .flowRecordFactory
            .createFlowEventRecord(eq(testContext.flowId), argThat<net.corda.data.flow.event.Wakeup> { true } )
        ).thenReturn(eventRecord)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).containsOnly(statusRecord,eventRecord)
    }
}