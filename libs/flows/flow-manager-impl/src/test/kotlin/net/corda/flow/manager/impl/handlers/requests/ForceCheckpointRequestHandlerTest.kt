package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.requests.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import net.corda.data.flow.state.waiting.Wakeup as WaitingForWakeup

class ForceCheckpointRequestHandlerTest {

    private val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))

    private val flowEvent = FlowEvent()

    private val handler = ForceCheckpointRequestHandler()

    @Test
    fun `Sets the waiting for property to Wakeup`() {
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowKey = this@ForceCheckpointRequestHandlerTest.flowKey
        }
        val inputContext = FlowEventContext<Any>(checkpoint, flowEvent, "doesn't matter", emptyList())
        val outputContext = handler.postProcess(inputContext, FlowIORequest.ForceCheckpoint)
        assertTrue(outputContext.checkpoint!!.flowState.waitingFor.value is WaitingForWakeup)
    }

    @Test
    fun `Creates a Wakeup record`() {
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowKey = this@ForceCheckpointRequestHandlerTest.flowKey
        }
        val inputContext = FlowEventContext<Any>(checkpoint, flowEvent, "doesn't matter", emptyList())
        val outputContext = handler.postProcess(inputContext, FlowIORequest.ForceCheckpoint)
        assertEquals(
            listOf(Record(FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, Wakeup()))),
            outputContext.outputRecords
        )
    }

    @Test
    fun `Throws if there is no checkpoint`() {
        val inputContext = FlowEventContext<Any>(checkpoint = null, flowEvent, "doesn't matter", emptyList())
        assertThrows<FlowProcessingException> {
            handler.postProcess(inputContext, FlowIORequest.ForceCheckpoint)
        }
    }
}