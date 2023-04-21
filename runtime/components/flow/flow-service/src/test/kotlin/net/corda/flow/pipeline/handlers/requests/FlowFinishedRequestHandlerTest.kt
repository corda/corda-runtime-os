package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.SESSION_ID_1
import net.corda.flow.fiber.FlowIORequest
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowFinishedRequestHandlerTest {

    private companion object {
        val FLOW_KEY = FlowKey(SESSION_ID_1, BOB_X500_HOLDING_IDENTITY)
    }

    private val testContext = RequestHandlerTestContext(Any())
    private val flowResult = "ok"
    private val ioRequest = FlowIORequest.FlowFinished(flowResult)
    private val handler = FlowFinishedRequestHandler(testContext.flowMessageFactory, testContext.flowRecordFactory)

    @Test
    fun `Updates the waiting for to nothing`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertThat(waitingFor.value).isNull()
    }

    @Test
    fun `post processing marks context as deleted`() {
        whenever(testContext.flowCheckpoint.flowKey).thenReturn(FLOW_KEY)
        handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowCheckpoint).markDeleted()
    }

    @Test
    fun `post processing publishes status update and schedules flow cleanup`() {
        val statusRecord = Record("", FLOW_KEY, FlowStatus())
        val cleanupRecord = Record("", FLOW_KEY.toString(), FlowMapperEvent())
        val flowStatus = FlowStatus()

        whenever(
            testContext.flowMessageFactory.createFlowCompleteStatusMessage(
                testContext.flowCheckpoint,
                flowResult
            )
        ).thenReturn(flowStatus)

        whenever(testContext.flowRecordFactory.createFlowStatusRecord(flowStatus)).thenReturn(statusRecord)
        whenever(testContext.flowRecordFactory.createFlowMapperEventRecord(eq(FLOW_KEY.toString()), any<ScheduleCleanup>()))
            .thenReturn(cleanupRecord)
        whenever(testContext.flowCheckpoint.flowKey).thenReturn(FLOW_KEY)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).containsOnly(statusRecord, cleanupRecord)
    }
}