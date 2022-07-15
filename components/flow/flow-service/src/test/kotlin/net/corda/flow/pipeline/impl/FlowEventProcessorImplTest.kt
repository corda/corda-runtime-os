package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.EMPTY_SMART_CONFIG
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowEventProcessorImplTest {

    private val wakeupPayload = Wakeup()
    private val flowKey = "flow id"
    private val flowCheckpoint = mock<FlowCheckpoint>()
    private val inputCheckpoint = Checkpoint()
    private val updatedCheckpoint = Checkpoint()
    private val outputRecords = listOf(Record(FLOW_EVENT_TOPIC, "key", "value"))
    private val updatedContext = buildFlowEventContext<Any>(
        flowCheckpoint,
        wakeupPayload,
        outputRecords = outputRecords
    )

    private val outputResponse = StateAndEventProcessor.Response<Checkpoint>(
        null,
        listOf<Record<String, String>>()
    )


    private val flowEventPipeline = mock<FlowEventPipeline>().apply {
        whenever(eventPreProcessing()).thenReturn(this)
        whenever(runOrContinue()).thenReturn(this)
        whenever(setCheckpointSuspendedOn()).thenReturn(this)
        whenever(setWaitingFor()).thenReturn(this)
        whenever(requestPostProcessing()).thenReturn(this)
        whenever(globalPostProcessing()).thenReturn(this)
        whenever(context).thenReturn(updatedContext)
    }
    private val flowEventExceptionProcessor = mock<FlowEventExceptionProcessor>()
    private val flowEventContextConverter = mock<FlowEventContextConverter>().apply {
        whenever(convert(updatedContext)).thenReturn(
            StateAndEventProcessor.Response(
                updatedCheckpoint,
                outputRecords
            )
        )
    }

    private val flowEventPipelineFactory = mock<FlowEventPipelineFactory>().apply {
        whenever(create(any(), any(), any())).thenReturn(flowEventPipeline)
    }

    private val processor = FlowEventProcessorImpl(
        flowEventPipelineFactory,
        flowEventExceptionProcessor,
        flowEventContextConverter,
        EMPTY_SMART_CONFIG
    )

    @Test
    fun `Returns the state unaltered if no flow event supplied`() {
        val inputEvent = getFlowEventRecord(null)

        val response = processor.onNext(inputCheckpoint, inputEvent)

        assertThat(response.updatedState).isSameAs(inputCheckpoint)
        assertThat(response.responseEvents).isEmpty()
    }

    @Test
    fun `Returns a checkpoint and events to send`() {
        val inputEvent = getFlowEventRecord(FlowEvent(flowKey, wakeupPayload))

        val response = processor.onNext(inputCheckpoint, inputEvent)

        assertEquals(updatedCheckpoint, response.updatedState)
        assertEquals(outputRecords, response.responseEvents)
    }

    @Test
    fun `Calls the pipeline steps in order`() {
        processor.onNext(Checkpoint(), getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))
        inOrder(flowEventPipeline) {
            verify(flowEventPipeline).eventPreProcessing()
            verify(flowEventPipeline).runOrContinue()
            verify(flowEventPipeline).setCheckpointSuspendedOn()
            verify(flowEventPipeline).setWaitingFor()
            verify(flowEventPipeline).requestPostProcessing()
            verify(flowEventPipeline).globalPostProcessing()
        }
    }

    @Test
    fun `Flow transient exception is handled`() {
        val error = FlowTransientException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error)).thenReturn(outputResponse)

        val response = processor.onNext(Checkpoint(), getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(outputResponse)
    }

    @Test
    fun `Flow event exception is handled`() {
        val error = FlowEventException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error)).thenReturn(outputResponse)

        val response = processor.onNext(Checkpoint(), getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(outputResponse)
    }

    @Test
    fun `Flow platform exception is handled`() {
        val error = FlowPlatformException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error)).thenReturn(outputResponse)

        val response = processor.onNext(Checkpoint(), getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(outputResponse)
    }

    @Test
    fun `Flow fatal exception is handled`() {
        val error = FlowFatalException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error)).thenReturn(outputResponse)

        val response = processor.onNext(Checkpoint(), getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(outputResponse)
    }

    @Test
    fun `Flow unexpected exception is handled`() {
        val error = IllegalStateException()

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error)).thenReturn(outputResponse)

        val response = processor.onNext(Checkpoint(), getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(outputResponse)
    }

    private fun getFlowEventRecord(flowEvent: FlowEvent?): Record<String, FlowEvent> {
        return Record(FLOW_EVENT_TOPIC, flowKey, flowEvent)
    }
}