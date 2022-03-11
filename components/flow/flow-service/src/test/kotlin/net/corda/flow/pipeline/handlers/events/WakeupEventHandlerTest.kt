package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
}