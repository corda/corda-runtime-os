package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.flow.state.waiting.Receive
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import net.corda.data.flow.state.waiting.Wakeup as WaitingForWakeup

class WakeupEventHandlerTest {

    private val wakeupPayload = Wakeup()

    private val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))

    private val flowEvent = FlowEvent(flowKey, wakeupPayload)

    private val handler = WakeUpEventHandler()

    @Test
    fun `preProcess does not modify the context`() {
        val inputContext = FlowEventContext(Checkpoint(), flowEvent, wakeupPayload, emptyList())
        assertEquals(inputContext, handler.preProcess(inputContext))
    }

    @Test
    fun `preProcess throws if a checkpoint does not exist`() {
        val inputContext = FlowEventContext(checkpoint = null, flowEvent, wakeupPayload, emptyList())
        assertThrows<FlowProcessingException> {
            handler.preProcess(inputContext)
        }
    }

    @Test
    fun `runOrContinue returns FlowContinuation#Run if it is waiting for a wakeup event`() {
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState().apply {
                waitingFor = WaitingFor(WaitingForWakeup())
            }
        }
        val inputContext = FlowEventContext(checkpoint, flowEvent, wakeupPayload, emptyList())
        assertEquals(FlowContinuation.Run(Unit), handler.runOrContinue(inputContext))
    }

    @Test
    fun `runOrContinue throws if it is not waiting for a wakeup event`() {
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState().apply {
                waitingFor = WaitingFor(Receive())
            }
        }
        val inputContext = FlowEventContext(checkpoint, flowEvent, wakeupPayload, emptyList())
        handler.runOrContinue(inputContext)
    }

    @Test
    fun `runOrContinue returns FlowContinuation#Continue if it is not waiting for anything`() {
        val checkpoint = Checkpoint().apply {
            flowState = StateMachineState().apply {
                waitingFor = null
            }
        }
        val inputContext = FlowEventContext(checkpoint, flowEvent, wakeupPayload, emptyList())
        assertThrows<FlowProcessingException> {
            handler.runOrContinue(inputContext)
        }
    }

    @Test
    fun `postProcess does not modify the context`() {
        val inputContext = FlowEventContext(Checkpoint(), flowEvent, wakeupPayload, emptyList())
        assertEquals(inputContext, handler.postProcess(inputContext))
    }
}