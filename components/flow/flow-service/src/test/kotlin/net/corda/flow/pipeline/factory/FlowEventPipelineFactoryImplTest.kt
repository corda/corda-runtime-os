package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.FLOW_ID_1
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.factory.impl.FlowEventPipelineFactoryImpl
import net.corda.flow.pipeline.handlers.events.FlowEventHandler
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.impl.FlowEventPipelineImpl
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowCheckpointFactory
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.uncheckedCast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowEventPipelineFactoryImplTest {

    private val wakeupPayload = Wakeup()
    private val flowEvent = FlowEvent(FLOW_ID_1, wakeupPayload)
    private val checkpoint = Checkpoint()
    private val flowCheckpoint = mock<FlowCheckpoint>()
    private val flowRunner = mock<FlowRunner>()
    private val config = mock<SmartConfig>()
    private val flowCheckpointFactory = mock<FlowCheckpointFactory>().apply {
        whenever(this.create(checkpoint, config)).thenReturn(flowCheckpoint)
    }
    private val flowGlobalPostProcessor = mock<FlowGlobalPostProcessor>()

    private val flowEventHandler = mock<FlowEventHandler<Any>>().apply {
        val casted: FlowEventHandler<Wakeup> = uncheckedCast(this)
        whenever(casted.type).thenReturn(Wakeup::class.java)
    }
    private val flowWaitingForHandler = mock<FlowWaitingForHandler<Any>>().apply {
        val casted: FlowWaitingForHandler<net.corda.data.flow.state.waiting.Wakeup> = uncheckedCast(this)
        whenever(casted.type).thenReturn(net.corda.data.flow.state.waiting.Wakeup::class.java)
    }
    private val flowRequestHandler = mock<FlowRequestHandler<FlowIORequest.ForceCheckpoint>>().apply {
        whenever(type).thenReturn(FlowIORequest.ForceCheckpoint::class.java)
    }

    private val factory = FlowEventPipelineFactoryImpl(
        flowRunner,
        flowGlobalPostProcessor,
        flowCheckpointFactory,
        listOf(flowEventHandler),
        listOf(flowWaitingForHandler),
        listOf(flowRequestHandler)
    )

    @Test
    fun `Creates a FlowEventPipeline instance`() {
        val expected = FlowEventPipelineImpl(
            mapOf(Wakeup::class.java to flowEventHandler),
            mapOf(net.corda.data.flow.state.waiting.Wakeup::class.java to flowWaitingForHandler),
            mapOf(FlowIORequest.ForceCheckpoint::class.java to flowRequestHandler),
            flowRunner,
            flowGlobalPostProcessor,
            buildFlowEventContext(flowCheckpoint, flowEvent.payload, config)
        )

        assertEquals(expected, factory.create(checkpoint, flowEvent, config))
    }
}