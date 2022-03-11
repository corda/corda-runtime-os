package net.corda.flow.acceptance

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
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
        val flowEventA = FlowEvent(FlowKey("a", HoldingIdentity("x500 name", "group id")), Wakeup())
        val flowEventB = FlowEvent(FlowKey("b", HoldingIdentity("x500 name", "group id")), StartFlow())
        const val notAFlowEvent = "not a flow event"
    }

    @Test
    fun filterOutputFlowTopicRecords() {
        val filtered = StateAndEventProcessor.Response(
            Checkpoint(),
            listOf(
                Record(Schemas.Flow.FLOW_EVENT_TOPIC, flowEventA.flowKey, flowEventA),
                Record(Schemas.Flow.FLOW_EVENT_TOPIC, flowEventB.flowKey, flowEventB),
                Record(Schemas.RPC.RPC_PERM_ENTITY_TOPIC, notAFlowEvent, notAFlowEvent)
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
                Record(Schemas.Flow.FLOW_EVENT_TOPIC, flowEventA.flowKey, flowEventA),
                Record(Schemas.Flow.FLOW_EVENT_TOPIC, flowEventB.flowKey, flowEventB),
                Record(Schemas.RPC.RPC_PERM_ENTITY_TOPIC, notAFlowEvent, notAFlowEvent)
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
                Record(Schemas.Flow.FLOW_EVENT_TOPIC, flowEventA.flowKey, flowEventA),
                Record(Schemas.Flow.FLOW_EVENT_TOPIC, flowEventB.flowKey, flowEventB),
                Record(Schemas.RPC.RPC_PERM_ENTITY_TOPIC, notAFlowEvent, notAFlowEvent)
            )
        ).filterOutputFlowTopicEventPayloads()

        assertEquals(2, filtered.size)
        assertTrue(flowEventA.payload in filtered)
        assertTrue(flowEventB.payload in filtered)
    }
}