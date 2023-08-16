package net.corda.flow.pipeline.impl

import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.metrics.FlowIORequestTypeConverter
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.runner.FlowRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowExecutionPipelineStageTest {

    private companion object {
        const val ACTION_NAME = "foo"
    }

    private val fiberFuture = mock<FiberFuture>()
    private val flowRunner = mock<FlowRunner>().apply {
        whenever(runFlow(any(), any())).thenReturn(fiberFuture)
    }

    private val waitingForHandler = mock<FlowWaitingForHandler<*>>()
    private val requestHandler = mock<FlowRequestHandler<*>>()

    private val fiberCache = mock<FlowFiberCache>()

    private val ioRequestTypeConverter = mock<FlowIORequestTypeConverter>().apply {
        whenever(convertToActionName(any())).thenReturn(ACTION_NAME)
    }
}