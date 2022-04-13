package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ForceCheckpointRequestHandlerTest {

    private val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))

    private val handler = ForceCheckpointRequestHandler()

    @Test
    fun `Updates the waiting for to Wakeup`() {
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowKey = this@ForceCheckpointRequestHandlerTest.flowKey
        }
        val inputContext = buildFlowEventContext<Any>(checkpoint, "doesn't matter")
        val result = handler.getUpdatedWaitingFor(inputContext, FlowIORequest.ForceCheckpoint)
        assertEquals(net.corda.data.flow.state.waiting.Wakeup(), result.value)
    }

    @Test
    fun `Creates a Wakeup record`() {
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState()
            flowKey = this@ForceCheckpointRequestHandlerTest.flowKey
        }
        val inputContext = buildFlowEventContext<Any>(checkpoint, "doesn't matter")
        val outputContext = handler.postProcess(inputContext, FlowIORequest.ForceCheckpoint)
        assertEquals(
            listOf(Record(FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, Wakeup()))),
            outputContext.outputRecords
        )
    }

    @Test
    fun `Throws if there is no checkpoint`() {
        val inputContext = buildFlowEventContext<Any>(checkpoint = null, "doesn't matter")
        assertThrows<FlowProcessingException> {
            handler.postProcess(inputContext, FlowIORequest.ForceCheckpoint)
        }
    }
}