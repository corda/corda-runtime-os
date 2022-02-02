package net.corda.flow.manager.impl.acceptance

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.FlowEventProcessor
import net.corda.flow.manager.impl.acceptance.dsl.MockFlowEventProcessor
import net.corda.flow.manager.impl.acceptance.dsl.MockFlowRunner
import net.corda.flow.manager.impl.acceptance.dsl.filterOutputFlowTopicEventPayloads
import net.corda.flow.manager.impl.acceptance.dsl.filterOutputFlowTopicEvents
import net.corda.flow.manager.impl.acceptance.dsl.filterOutputFlowTopicRecords
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MockFlowEventProcessorTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val CLIENT_ID = "client id"

        val flowEventA = FlowEvent(FlowKey("a", HoldingIdentity("x500 name", "group id")), Wakeup())
        val flowEventB = FlowEvent(FlowKey("b", HoldingIdentity("x500 name", "group id")), StartRPCFlow())
        const val notAFlowEvent = "not a flow event"
    }

    private val runner = MockFlowRunner()
    private val delegate: FlowEventProcessor = mock()
    private val processor = MockFlowEventProcessor(delegate, runner)

    @Test
    fun `startFlow creates and executes the first step of a new flow`() {
        whenever(delegate.onNext(eq(null), any())).thenReturn(StateAndEventProcessor.Response(Checkpoint(), emptyList()))
        val (fiber, _) = processor.startFlow(FLOW_ID, CLIENT_ID)
        assertEquals(FLOW_ID, fiber.flowId)
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