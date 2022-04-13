package net.corda.flow.acceptance

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.acceptance.dsl.filterOutputFlowTopicEventPayloads
import net.corda.flow.acceptance.dsl.filterOutputFlowTopicEvents
import net.corda.flow.acceptance.dsl.filterOutputFlowTopicRecords
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StateAndEventProcessorResponseUtilsTest {

    private companion object {
        val flowEventA = FlowEvent("a", Wakeup())
        val flowEventB = FlowEvent("b", StartFlow())
        const val notAFlowEvent = "not a flow event"
    }

    private fun getFlowEventRecord(flowEvent:FlowEvent): Record<String,FlowEvent>{
        return Record(Schemas.Flow.FLOW_EVENT_TOPIC, flowEvent.flowId, flowEvent)
    }

    private fun getNotAFlowEventRecord(): Record<String,String>{
        return Record("not a flow event topic", notAFlowEvent, notAFlowEvent)
    }

    @Test
    fun filterOutputFlowTopicRecords() {
        val filtered = StateAndEventProcessor.Response(
            Checkpoint(),
            listOf(
                getFlowEventRecord(flowEventA),
                getFlowEventRecord(flowEventB),
                getNotAFlowEventRecord(),
            )
        ).filterOutputFlowTopicRecords()

        assertEquals(2, filtered.size)
        assertTrue(flowEventA in filtered.map { it.value })
        assertTrue(flowEventB in filtered.map { it.value })
    }

    @Test
    fun filterOutputFlowTopicEvents() {
        val filtered = StateAndEventProcessor.Response(
            Checkpoint(),
            listOf(
                getFlowEventRecord(flowEventA),
                getFlowEventRecord(flowEventB),
                getNotAFlowEventRecord(),
            )
        ).filterOutputFlowTopicEvents()

        assertEquals(2, filtered.size)
        assertTrue(flowEventA in filtered)
        assertTrue(flowEventB in filtered)
    }

    @Test
    fun filterOutputFlowTopicEventPayloads() {
        val filtered = StateAndEventProcessor.Response(
            Checkpoint(),
            listOf(
                getFlowEventRecord(flowEventA),
                getFlowEventRecord(flowEventB),
                getNotAFlowEventRecord(),
            )
        ).filterOutputFlowTopicEventPayloads()

        assertEquals(2, filtered.size)
        assertTrue(flowEventA.payload in filtered)
        assertTrue(flowEventB.payload in filtered)
    }
}