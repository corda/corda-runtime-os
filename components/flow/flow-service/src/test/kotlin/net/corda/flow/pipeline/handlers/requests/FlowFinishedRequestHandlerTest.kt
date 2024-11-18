package net.corda.flow.pipeline.handlers.requests

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.SESSION_ID_1
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowFinishedRequestHandlerTest {

    private companion object {
        val FLOW_KEY = FlowKey(SESSION_ID_1, BOB_X500_HOLDING_IDENTITY)
    }

    private val flowResult = "ok"
    private val ioRequest = FlowIORequest.FlowFinished(flowResult)
    private val avroSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val serializer = mock<CordaAvroSerializer<Any>>()
    private lateinit var testContext: RequestHandlerTestContext<Any>
    private lateinit var handler: FlowFinishedRequestHandler

    @BeforeEach
    fun setup() {
        whenever(serializer.serialize(any())).thenReturn(byteArrayOf(1, 2, 3))
        whenever(avroSerializationFactory.createAvroSerializer<Any>(anyOrNull())).thenReturn(serializer)
        testContext = RequestHandlerTestContext(Any())
        whenever(testContext.flowCheckpoint.maxMessageSize).thenReturn(100000000)
        handler = FlowFinishedRequestHandler(testContext.flowMessageFactory, testContext.flowRecordFactory, avroSerializationFactory)
    }

    @Test
    fun `Updates the waiting for to nothing`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertThat(waitingFor?.value).isNull()
    }

    @Test
    fun `Flow Result exceeds max size`() {
        whenever(testContext.flowCheckpoint.maxMessageSize).thenReturn(1)
        handler = FlowFinishedRequestHandler(testContext.flowMessageFactory, testContext.flowRecordFactory, avroSerializationFactory)

        assertThrows<FlowFatalException> {  handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest) }
    }

    @Test
    fun `post processing marks context as deleted`() {
        val flowStatus = FlowStatus()
        whenever(
            testContext.flowMessageFactory.createFlowCompleteStatusMessage(
                testContext.flowCheckpoint,
                flowResult
            )
        ).thenReturn(flowStatus)
        whenever(testContext.flowCheckpoint.flowKey).thenReturn(FLOW_KEY)
        handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowCheckpoint).markDeleted()
    }

    @Test
    fun `post processing publishes status update and does not schedule flow cleanup`() {
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
        assertThat(outputContext.outputRecords).containsOnly(statusRecord)
    }
}