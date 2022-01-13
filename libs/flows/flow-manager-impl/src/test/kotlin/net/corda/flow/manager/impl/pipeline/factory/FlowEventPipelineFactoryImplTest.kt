package net.corda.flow.manager.impl.pipeline.factory

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.requests.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.manager.impl.handlers.events.FlowEventHandler
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.pipeline.FlowEventPipelineImpl
import net.corda.flow.manager.impl.runner.FlowRunner
import net.corda.v5.base.util.uncheckedCast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowEventPipelineFactoryImplTest {

    private val wakeupPayload = Wakeup()
    private val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))
    private val flowEvent = FlowEvent(flowKey, wakeupPayload)

    private val flowRunner = mock<FlowRunner>()

    private val flowEventHandler = mock<FlowEventHandler<Any>>().apply {
        val casted: FlowEventHandler<Wakeup> = uncheckedCast(this)
        whenever(casted.type).thenReturn(Wakeup::class.java)
    }
    private val flowRequestHandler = mock<FlowRequestHandler<FlowIORequest.ForceCheckpoint>>().apply {
        whenever(type).thenReturn(FlowIORequest.ForceCheckpoint::class.java)
    }

    private val factory = FlowEventPipelineFactoryImpl(flowRunner, listOf(flowEventHandler), listOf(flowRequestHandler))

    @Test
    fun `Creates a FlowEventPipeline instance`() {
        val checkpoint = Checkpoint()
        val expected = FlowEventPipelineImpl(
            flowEventHandler,
            mapOf(FlowIORequest.ForceCheckpoint::class.java to flowRequestHandler),
            flowRunner,
            FlowEventContext(
                checkpoint,
                flowEvent,
                flowEvent.payload,
                emptyList()
            )
        )
        assertEquals(expected, factory.create(checkpoint, flowEvent))
    }

    @Test
    fun `Throws a FlowProcessingException if there is no matching event handler`() {
        val factory = FlowEventPipelineFactoryImpl(flowRunner, emptyList(), listOf(flowRequestHandler))
        assertThrows<FlowProcessingException> {
            factory.create(Checkpoint(), flowEvent)
        }
    }
}