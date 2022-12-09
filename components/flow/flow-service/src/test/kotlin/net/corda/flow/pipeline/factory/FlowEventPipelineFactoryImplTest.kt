package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.checkpoint.Checkpoint
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
import net.corda.flow.state.impl.FlowCheckpointFactory
import net.corda.flow.test.utils.buildFlowEventContext
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
    private val flowEventContext = buildFlowEventContext(flowCheckpoint, flowEvent.payload)

    private val config = flowEventContext.config
    private val flowCheckpointFactory = mock<FlowCheckpointFactory>().also { factory ->
        whenever(factory.create(FLOW_ID_1, checkpoint, config)).thenReturn(flowCheckpoint)
    }
    private val flowGlobalPostProcessor = mock<FlowGlobalPostProcessor>()

    private val flowEventHandler = mock<FlowEventHandler<Any>>().also { handler ->
        @Suppress("unchecked_cast")
        val casted = handler as FlowEventHandler<Wakeup>
        whenever(casted.type).thenReturn(Wakeup::class.java)
    }
    private val flowWaitingForHandler = mock<FlowWaitingForHandler<Any>>().also { flowWaiting ->
        @Suppress("unchecked_cast")
        val casted = flowWaiting as FlowWaitingForHandler<net.corda.data.flow.state.waiting.Wakeup>
        whenever(casted.type).thenReturn(net.corda.data.flow.state.waiting.Wakeup::class.java)
    }
    private val flowRequestHandler = mock<FlowRequestHandler<FlowIORequest.ForceCheckpoint>>().also { handler ->
        whenever(handler.type).thenReturn(FlowIORequest.ForceCheckpoint::class.java)
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
            flowEventContext
        )
        val result = factory.create(checkpoint, flowEvent, config, emptyMap())
        assertEquals(expected.context, result.context)
    }
}