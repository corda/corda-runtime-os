package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

class StartFlowEventHandlerTest {

    private val startFlow = StartFlow(FlowStartContext(),"start args")
    private val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))
    private val flowEvent = FlowEvent(flowKey, startFlow)
    private val handler = StartFlowEventHandler()

    @Test
    fun `preProcess creates a new checkpoint if one doesn't exist already`() {
        val inputContext = FlowEventContext(checkpoint = null, flowEvent, startFlow, emptyList())
        val outputContext = handler.preProcess(inputContext)
        assertNotNull(outputContext.checkpoint)
        assertEquals(flowKey, outputContext.checkpoint!!.flowKey)
        assertEquals(ByteBuffer.wrap(byteArrayOf()), outputContext.checkpoint!!.fiber)
        assertEquals(startFlow.startContext, outputContext.checkpoint!!.flowStartContext)
    }

    @Test
    fun `preProcess throws if a checkpoint already exists`() {
        val inputContext = FlowEventContext(Checkpoint(), flowEvent, startFlow, emptyList())
        assertThrows<FlowProcessingException> {
            handler.preProcess(inputContext)
        }
    }

    @Test
    fun `runOrContinue returns FlowContinuation#Run`() {
        val inputContext = FlowEventContext(Checkpoint(), flowEvent, startFlow, emptyList())
        assertEquals(FlowContinuation.Run(Unit), handler.runOrContinue(inputContext))
    }

    @Test
    fun `postProcess does not modify the context`() {
        val inputContext = FlowEventContext(Checkpoint(), flowEvent, startFlow, emptyList())
        assertEquals(inputContext, handler.postProcess(inputContext))
    }
}