package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ForceCheckpointRequestHandlerTest {

    private val payload = Wakeup()
    private val flowRecordFactory = mock<FlowRecordFactory>()
    private val handler = ForceCheckpointRequestHandler(flowRecordFactory)
    private val testContext = RequestHandlerTestContext(Any())

    @Test
    fun `Updates the waiting for to Wakeup`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, FlowIORequest.ForceCheckpoint)
        assertTrue(waitingFor.value is net.corda.data.flow.state.waiting.Wakeup)
    }

    @Test
    fun `Creates a Wakeup record`() {
        val record = Record("","", FlowEvent())

        whenever(flowRecordFactory.createFlowEventRecord(testContext.flowId, payload)).thenReturn(record)

        val outputContext = handler.postProcess(testContext.flowEventContext, FlowIORequest.ForceCheckpoint)
        assertThat(outputContext.outputRecords).containsOnly(record)
    }
}