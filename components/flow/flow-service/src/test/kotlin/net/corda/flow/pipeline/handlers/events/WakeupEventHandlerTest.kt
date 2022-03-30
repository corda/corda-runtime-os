package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WakeupEventHandlerTest {

    private val wakeupPayload = Wakeup()

    private val handler = WakeupEventHandler()

    @Test
    fun `Does not modify the context`() {
        val inputContext = buildFlowEventContext(Checkpoint(), wakeupPayload)
        assertEquals(inputContext, handler.preProcess(inputContext))
    }

    @Test
    fun `Throws if a checkpoint does not exist`() {
        val inputContext = buildFlowEventContext(checkpoint = null, wakeupPayload)
        assertThrows<FlowProcessingException> {
            handler.preProcess(inputContext)
        }
    }
}