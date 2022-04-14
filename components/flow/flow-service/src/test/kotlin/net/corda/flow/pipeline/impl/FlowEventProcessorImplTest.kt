package net.corda.flow.pipeline.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.FlowHospitalException
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowEventProcessorImplTest {

    private val wakeupPayload = Wakeup()
    private val flowKey = "flow id"
    private val inputCheckpoint = mock<FlowCheckpoint>()
    private val updatedCheckpoint = Checkpoint()
    private val outputRecords = listOf(Record(FLOW_EVENT_TOPIC, "key", "value"))
    private val updatedContext = buildFlowEventContext<Any>(
        inputCheckpoint,
        wakeupPayload,
        outputRecords = outputRecords
    )

    private val flowEventPipeline = mock<FlowEventPipeline>().apply {
        whenever(eventPreProcessing()).thenReturn(this)
        whenever(runOrContinue()).thenReturn(this)
        whenever(setCheckpointSuspendedOn()).thenReturn(this)
        whenever(setWaitingFor()).thenReturn(this)
        whenever(requestPostProcessing()).thenReturn(this)
        whenever(globalPostProcessing()).thenReturn(this)
        whenever(toStateAndEventResponse()).thenReturn(StateAndEventProcessor.Response(updatedCheckpoint, outputRecords))
    }
    private val flowEventPipelineFactory = mock<FlowEventPipelineFactory>().apply {
        whenever(create(any(), any(), any())).thenReturn(flowEventPipeline)
    }

    private val processor = FlowEventProcessorImpl(flowEventPipelineFactory, mock())

    @Test
    fun `Throws FlowHospitalException if there was no flow event`() {
        assertThrows<FlowHospitalException> {
            processor.onNext(Checkpoint(), Record(FLOW_EVENT_TOPIC, flowKey, null))
        }
    }

    @Test
    fun `Returns a checkpoint and events to send`() {
        val response = processor.onNext(Checkpoint(), Record(FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, wakeupPayload)))
        assertEquals(updatedCheckpoint, response.updatedState)
        assertEquals(outputRecords, response.responseEvents)
    }

    @Test
    fun `Calls the pipeline steps in order`() {
        processor.onNext(Checkpoint(), Record(FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, wakeupPayload)))
        inOrder(flowEventPipeline) {
            verify(flowEventPipeline).eventPreProcessing()
            verify(flowEventPipeline).runOrContinue()
            verify(flowEventPipeline).setCheckpointSuspendedOn()
            verify(flowEventPipeline).setWaitingFor()
            verify(flowEventPipeline).requestPostProcessing()
            verify(flowEventPipeline).globalPostProcessing()
            verify(flowEventPipeline).toStateAndEventResponse()
        }
    }

    @Test
    fun `Returns the existing checkpoint and no records if there was an FlowProcessingException when executing the pipeline`() {
        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(FlowProcessingException("Broken"))
        val response = processor.onNext(Checkpoint(), Record(FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, wakeupPayload)))
        assertEquals(Checkpoint(), response.updatedState)
        assertEquals(emptyList<Record<FlowKey, FlowEvent>>(), response.responseEvents)
    }

    @Test
    fun `Returns the existing checkpoint and no records if there was an unknown exception when executing the pipeline`() {
        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(IllegalStateException("Broken"))
        assertThrows<IllegalStateException> {
            processor.onNext(Checkpoint(), Record(FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, wakeupPayload)))
        }
    }
}