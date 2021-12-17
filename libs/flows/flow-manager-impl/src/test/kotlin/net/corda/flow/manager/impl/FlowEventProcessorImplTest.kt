package net.corda.flow.manager.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.requests.FlowIORequest
import net.corda.flow.manager.exceptions.FlowHospitalException
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.manager.impl.handlers.events.FlowEventHandler
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.runner.FlowRunner
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.uncheckedCast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowEventProcessorImplTest {

    private val wakeupPayload = Wakeup()
    private val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))
    private val flowEvent = FlowEvent(flowKey, wakeupPayload)
    private val updatedCheckpoint = Checkpoint().apply {
        cpiId = "cpi id set"
    }
    private val outputRecords = listOf(Record(Schemas.FLOW_EVENT_TOPIC, "key", "value"))

    private val updatedContext = FlowEventContext<Any>(updatedCheckpoint, flowEvent, wakeupPayload, outputRecords)

    private val flowRunner = mock<FlowRunner>()
    private val flowEventHandler = mock<FlowEventHandler<Any>>().apply {
        val casted: FlowEventHandler<Wakeup> = uncheckedCast(this)
        whenever(casted.type).thenReturn(Wakeup::class.java)
        whenever(preProcess(any())).thenReturn(updatedContext)
        whenever(runOrContinue(any())).thenReturn(FlowContinuation.Continue)
        whenever(postProcess(any())).thenReturn(updatedContext)
    }
    private val flowRequestHandler = mock<FlowRequestHandler<FlowIORequest.ForceCheckpoint>>().apply {
        whenever(type).thenReturn(FlowIORequest.ForceCheckpoint::class.java)
        whenever(postProcess(any(), any())).thenReturn(updatedContext)
    }
    private val processor = FlowEventProcessorImpl(flowRunner, listOf(flowEventHandler), listOf(flowRequestHandler))

    @Test
    fun `Throws FlowHospitalException if there was no flow event`() {
        assertThrows<FlowHospitalException> {
            processor.onNext(Checkpoint(), Record(Schemas.FLOW_EVENT_TOPIC, flowKey, null))
        }
    }

    @Test
    fun `Returns a checkpoint and events to send`() {
        val response = processor.onNext(Checkpoint(), Record(Schemas.FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, wakeupPayload)))
        assertEquals(updatedCheckpoint, response.updatedState)
        assertEquals(outputRecords, response.responseEvents)
    }

    @Test
    fun `Returns the existing checkpoint and no records if there is no matching even handler`() {
        val processor = FlowEventProcessorImpl(flowRunner, emptyList(), listOf(flowRequestHandler))
        val response = processor.onNext(Checkpoint(), Record(Schemas.FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, wakeupPayload)))
        assertEquals(Checkpoint(), response.updatedState)
        assertEquals(emptyList<Record<FlowKey, FlowEvent>>(), response.responseEvents)
    }

    @Test
    fun `Returns the existing checkpoint and no records if there was an FlowProcessingException when executing the pipeline`() {
        whenever(flowEventHandler.postProcess(any())).thenThrow(FlowProcessingException("Broken"))
        val response = processor.onNext(Checkpoint(), Record(Schemas.FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, wakeupPayload)))
        assertEquals(Checkpoint(), response.updatedState)
        assertEquals(emptyList<Record<FlowKey, FlowEvent>>(), response.responseEvents)
    }

    @Test
    fun `Returns the existing checkpoint and no records if there was an unknown exception when executing the pipeline`() {
        whenever(flowEventHandler.postProcess(any())).thenThrow(IllegalStateException("Broken"))
        assertThrows<IllegalStateException> {
            processor.onNext(Checkpoint(), Record(Schemas.FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, wakeupPayload)))
        }
    }
}