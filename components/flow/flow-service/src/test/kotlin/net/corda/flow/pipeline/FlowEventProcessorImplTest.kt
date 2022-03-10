package net.corda.flow.pipeline

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.pipeline.impl.FlowEventPipelineImpl
import net.corda.flow.pipeline.impl.FlowEventProcessorImpl
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
    private val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))
    private val updatedCheckpoint = Checkpoint()
    private val outputRecords = listOf(Record(FLOW_EVENT_TOPIC, "key", "value"))
    private val updatedContext = FlowEventContext<Any>(updatedCheckpoint, FlowEvent(flowKey, wakeupPayload), wakeupPayload, outputRecords)

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
        whenever(create(any(), any())).thenReturn(flowEventPipeline)
    }

    private val processor = FlowEventProcessorImpl(flowEventPipelineFactory)

    @Test
    fun `Throws FlowHospitalException if there was no flow event`() {
        assertThrows<FlowHospitalException> {
            processor.onNext(Checkpoint(), Record(FLOW_EVENT_TOPIC, flowKey, null))
        }
    }

    @Test
    fun `Returns a checkpoint and events to send`() {
        whenever(flowEventPipeline.globalPostProcessing()).thenReturn(
            FlowEventPipelineImpl(
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                updatedContext
            )
        )
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