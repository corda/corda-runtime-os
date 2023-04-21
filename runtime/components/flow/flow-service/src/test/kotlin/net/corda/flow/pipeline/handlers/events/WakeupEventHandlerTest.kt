package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.Wakeup
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WakeupEventHandlerTest {

    private val checkpoint = mock<FlowCheckpoint>()
    private val handler = WakeupEventHandler()

    @Test
    fun `When the checkpoint exists the context is returned unmodified`() {
        whenever(checkpoint.doesExist).thenReturn(true)
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = Wakeup())
        val outputContext = handler.preProcess(inputContext)
        assertEquals(inputContext, outputContext)
    }

    @Test
    fun `When the checkpoint does not exist an exception is thrown to discard the event`() {
        whenever(checkpoint.doesExist).thenReturn(false)
        assertThrows<FlowEventException> {
            handler.preProcess(buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = Wakeup()))
        }
    }
}