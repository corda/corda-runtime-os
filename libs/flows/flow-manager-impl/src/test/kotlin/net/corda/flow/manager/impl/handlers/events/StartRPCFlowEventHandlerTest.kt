package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import java.time.Instant

class StartRPCFlowEventHandlerTest {

    private val startRPCFlowPayload = StartRPCFlow.newBuilder()
        .setClientId("client id")
        .setCpiId("cpi id")
        .setFlowClassName("flow class name")
        .setRpcUsername(HoldingIdentity("x500 name", "group id"))
        .setTimestamp(Instant.now())
        .setJsonArgs(" { \"json\": \"args\" }")
        .build()

    private val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))

    private val flowEvent = FlowEvent(flowKey, startRPCFlowPayload)

    private val handler = StartRPCFlowEventHandler()

    @Test
    fun `preProcess creates a new checkpoint if one doesn't exist already`() {
        val inputContext = FlowEventContext(checkpoint = null, flowEvent, startRPCFlowPayload, emptyList())
        val outputContext = handler.preProcess(inputContext)
        assertNotNull(outputContext.checkpoint)
        assertEquals(flowKey, outputContext.checkpoint!!.flowKey)
        assertEquals(startRPCFlowPayload.cpiId, outputContext.checkpoint!!.cpiId)
        assertEquals(ByteBuffer.wrap(byteArrayOf()), outputContext.checkpoint!!.fiber)
        assertEquals(startRPCFlowPayload.clientId, outputContext.checkpoint!!.flowState.clientId)
    }

    @Test
    fun `preProcess throws if a checkpoint already exists`() {
        val inputContext = FlowEventContext(Checkpoint(), flowEvent, startRPCFlowPayload, emptyList())
        assertThrows<FlowProcessingException> {
            handler.preProcess(inputContext)
        }
    }

    @Test
    fun `runOrContinue returns FlowContinuation#Run`() {
        val inputContext = FlowEventContext(Checkpoint(), flowEvent, startRPCFlowPayload, emptyList())
        assertEquals(FlowContinuation.Run(Unit), handler.runOrContinue(inputContext))
    }

    @Test
    fun `postProcess does not modify the context`() {
        val inputContext = FlowEventContext(Checkpoint(), flowEvent, startRPCFlowPayload, emptyList())
        assertEquals(inputContext, handler.postProcess(inputContext))
    }
}