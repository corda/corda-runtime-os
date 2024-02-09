package net.corda.flow.pipeline.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.external.ExternalEventResponse
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.Interruptable
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.metrics.FlowIORequestTypeConverter
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.metrics.FlowMetrics
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.flow.state.FlowCheckpoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.AdditionalAnswers
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException

class FlowExecutionPipelineStageTest {

    private companion object {
        private const val ACTION_NAME = "foo"
        private const val TIMEOUT = 1000L
        private val flowKey = FlowKey("foo", HoldingIdentity("name", "group"))
    }

    private val fiberCache = mock<FlowFiberCache>()
    private val metrics = mock<FlowMetrics>()

    private val ioRequestTypeConverter = mock<FlowIORequestTypeConverter>().apply {
        whenever(convertToActionName(any())).thenReturn(ACTION_NAME)
    }

    private val fiberOutput = FlowIORequest.ForceCheckpoint

    private val flowSuspended = FlowIORequest.FlowSuspended(
        ByteBuffer.wrap(byteArrayOf()),
        fiberOutput
    )

    @Test
    fun `when the fiber is run once, context is updated correctly`() {
        // The choice of waiting for values doesn't matter here.
        val waitingForHandlers = createWaitingForHandlerMap(
            mapOf(
                WaitingFor(SessionConfirmation()) to FlowContinuation.Run(),
                WaitingFor(ExternalEventResponse()) to FlowContinuation.Continue
            )
        )
        val checkpoint = mock<FlowCheckpoint>()
        val newContext = createContext(waitingFor = WaitingFor(ExternalEventResponse()))
        val requestHandlers = createRequestHandlerMap(
            mapOf(fiberOutput to Pair(WaitingFor(ExternalEventResponse()), newContext))
        )
        val fiberOutputs = listOf(flowSuspended)
        val flowRunner = createFlowRunner(fiberOutputs)
        val stage = FlowExecutionPipelineStage(
            waitingForHandlers,
            requestHandlers,
            flowRunner,
            fiberCache,
            ioRequestTypeConverter
        )

        var contextUpdateCount = 0
        val context = createContext(checkpoint = checkpoint)
        val outputContext = stage.runFlow(context, TIMEOUT) {
            contextUpdateCount++
        }
        assertEquals(1, contextUpdateCount)
        assertEquals(newContext, outputContext)
        verifyInteractions(fiberOutputs)
        verify(checkpoint).waitingFor = WaitingFor(ExternalEventResponse())
        verify(checkpoint).suspendedOn = fiberOutput::class.qualifiedName
    }

    @Test
    fun `when the fiber is run more than once, context is updated correctly`() {
        val waitingForHandlers = createWaitingForHandlerMap(
            mapOf(
                WaitingFor(SessionConfirmation()) to FlowContinuation.Run(),
                WaitingFor(ExternalEventResponse()) to FlowContinuation.Run()
            )
        )
        val checkpoint = mock<FlowCheckpoint>()
        val newCheckpoint = mock<FlowCheckpoint>()
        val newContext = createContext(checkpoint = newCheckpoint, waitingFor = WaitingFor(ExternalEventResponse()))
        val secondContext = createContext(waitingFor = null)
        val requestHandlers = createRequestHandlerMap(
            mapOf(
                fiberOutput to Pair(WaitingFor(ExternalEventResponse()), newContext),
                FlowIORequest.FlowFinished("bar") to Pair(null, secondContext)
                )
        )
        val fiberOutputs = listOf(flowSuspended, FlowIORequest.FlowFinished("bar"))
        val flowRunner = createFlowRunner(fiberOutputs)
        val stage = FlowExecutionPipelineStage(
            waitingForHandlers,
            requestHandlers,
            flowRunner,
            fiberCache,
            ioRequestTypeConverter
        )

        val context = createContext(checkpoint = checkpoint)
        var contextUpdateCount = 0
        val outputContext = stage.runFlow(context, TIMEOUT) {
            contextUpdateCount++
        }
        assertEquals(secondContext, outputContext)
        assertEquals(2, contextUpdateCount)
        verifyInteractions(fiberOutputs)
        verify(checkpoint).waitingFor = WaitingFor(ExternalEventResponse())
        verify(checkpoint).suspendedOn = fiberOutput::class.qualifiedName
        verify(newCheckpoint).waitingFor = null
        verify(newCheckpoint).suspendedOn = FlowIORequest.FlowFinished::class.qualifiedName
    }

    @Test
    fun `when the fiber is not run at all, context is not updated`() {
        val waitingForHandlers = createWaitingForHandlerMap(
            mapOf(
                WaitingFor(SessionConfirmation()) to FlowContinuation.Continue,
                WaitingFor(ExternalEventResponse()) to FlowContinuation.Continue
            )
        )
        val checkpoint = mock<FlowCheckpoint>()
        val newContext = createContext(waitingFor = WaitingFor(ExternalEventResponse()))
        val requestHandlers = createRequestHandlerMap(
            mapOf(fiberOutput to Pair(WaitingFor(ExternalEventResponse()), newContext))
        )
        val fiberOutputs = listOf<FlowIORequest<Any?>>()
        val flowRunner = createFlowRunner(fiberOutputs)
        val stage = FlowExecutionPipelineStage(
            waitingForHandlers,
            requestHandlers,
            flowRunner,
            fiberCache,
            ioRequestTypeConverter
        )

        val context = createContext(checkpoint = checkpoint)
        var contextUpdateCount = 0
        val outputContext = stage.runFlow(context, TIMEOUT) {
            contextUpdateCount++
        }
        assertEquals(context, outputContext)
        assertEquals(0, contextUpdateCount)
        verifyInteractions(fiberOutputs)
        verify(checkpoint, never()).waitingFor = any()
        verify(checkpoint, never()).suspendedOn = any()
    }

    @Test
    fun `when the flow is to be resumed with an error, context is updated correctly`() {
        // The choice of waiting for values doesn't matter here.
        val waitingForHandlers = createWaitingForHandlerMap(
            mapOf(
                WaitingFor(SessionConfirmation()) to FlowContinuation.Error(Exception()),
                WaitingFor(ExternalEventResponse()) to FlowContinuation.Continue
            )
        )
        val checkpoint = mock<FlowCheckpoint>()
        val newContext = createContext(waitingFor = WaitingFor(ExternalEventResponse()))
        val requestHandlers = createRequestHandlerMap(
            mapOf(fiberOutput to Pair(WaitingFor(ExternalEventResponse()), newContext))
        )
        val fiberOutputs = listOf(flowSuspended)
        val flowRunner = createFlowRunner(fiberOutputs)
        val stage = FlowExecutionPipelineStage(
            waitingForHandlers,
            requestHandlers,
            flowRunner,
            fiberCache,
            ioRequestTypeConverter
        )

        val context = createContext(checkpoint = checkpoint)
        val outputContext = stage.runFlow(context, TIMEOUT) {}
        assertEquals(newContext, outputContext)
        verifyInteractions(fiberOutputs)
        verify(checkpoint).waitingFor = WaitingFor(ExternalEventResponse())
        verify(checkpoint).suspendedOn = fiberOutput::class.qualifiedName
    }

    @Test
    fun `when the flow fails, context is updated correctly`() {
        val waitingForHandlers = createWaitingForHandlerMap(
            mapOf(
                WaitingFor(SessionConfirmation()) to FlowContinuation.Run()
            )
        )
        val checkpoint = mock<FlowCheckpoint>()
        val newContext = createContext(waitingFor = null)
        val requestHandlers = createRequestHandlerMap(
            mapOf(FlowIORequest.FlowFailed(IllegalArgumentException()) to Pair(null, newContext))
        )
        val fiberOutputs = listOf(FlowIORequest.FlowFailed(IllegalArgumentException()))
        val flowRunner = createFlowRunner(fiberOutputs)
        val stage = FlowExecutionPipelineStage(
            waitingForHandlers,
            requestHandlers,
            flowRunner,
            fiberCache,
            ioRequestTypeConverter
        )

        val context = createContext(checkpoint = checkpoint)
        val outputContext = stage.runFlow(context, TIMEOUT) {}
        assertEquals(newContext, outputContext)
        verifyInteractions(fiberOutputs)
        verify(checkpoint).waitingFor = null
        verify(checkpoint).suspendedOn = FlowIORequest.FlowFailed::class.qualifiedName
    }

    @Test
    fun `when the timer expires for running the flow, fail it and attempt to interrupt`() {
        val waitingForHandlers = createWaitingForHandlerMap(
            mapOf(
                WaitingFor(SessionConfirmation()) to FlowContinuation.Run(),
                WaitingFor(ExternalEventResponse()) to FlowContinuation.Continue
            )
        )
        val checkpoint = mock<FlowCheckpoint>()
        val newContext = createContext(waitingFor = WaitingFor(ExternalEventResponse()))
        val requestHandlers = createRequestHandlerMap(
            mapOf(
                fiberOutput to Pair(WaitingFor(ExternalEventResponse()), newContext),
                FlowIORequest.FlowFailed(IllegalArgumentException()) to Pair(null, newContext)
            )
        )
        val fiberOutputs = listOf(FlowIORequest.FlowFailed(IllegalArgumentException()))
        val fiberFuture = mock<FiberFuture>()
        val flowRunner = createFlowRunner(fiberOutputs, timeoutFlow = true, fiberFuture = fiberFuture)
        val stage = FlowExecutionPipelineStage(
            waitingForHandlers,
            requestHandlers,
            flowRunner,
            fiberCache,
            ioRequestTypeConverter
        )

        val context = createContext(checkpoint = checkpoint)
        val outputContext = stage.runFlow(context, TIMEOUT) {}
        assertEquals(newContext, outputContext)
        verifyInteractions(fiberOutputs)
        verify(checkpoint).waitingFor = null
        verify(checkpoint).suspendedOn = FlowIORequest.FlowFailed::class.qualifiedName
        verify(fiberFuture).interruptable
    }

    @Test
    fun `when there is no suitable waitingFor handler, throws fatal exception`() {
        val waitingForHandlers = createWaitingForHandlerMap(
            mapOf(
            )
        )
        val newContext = createContext(waitingFor = WaitingFor(ExternalEventResponse()))
        val requestHandlers = createRequestHandlerMap(
            mapOf(fiberOutput to Pair(WaitingFor(ExternalEventResponse()), newContext))
        )
        val fiberOutputs = listOf(flowSuspended)
        val flowRunner = createFlowRunner(fiberOutputs)
        val stage = FlowExecutionPipelineStage(
            waitingForHandlers,
            requestHandlers,
            flowRunner,
            fiberCache,
            ioRequestTypeConverter
        )

        val context = createContext()
        assertThrows<FlowFatalException> {
            stage.runFlow(context, TIMEOUT) {}
        }
    }

    @Test
    fun `when there is no suitable request handler, throws fatal exception`() {
        val waitingForHandlers = createWaitingForHandlerMap(
            mapOf(
                WaitingFor(SessionConfirmation()) to FlowContinuation.Run(),
                WaitingFor(ExternalEventResponse()) to FlowContinuation.Continue
            )
        )
        val requestHandlers = createRequestHandlerMap(
            mapOf()
        )
        val fiberOutputs = listOf(flowSuspended)
        val flowRunner = createFlowRunner(fiberOutputs)
        val stage = FlowExecutionPipelineStage(
            waitingForHandlers,
            requestHandlers,
            flowRunner,
            fiberCache,
            ioRequestTypeConverter
        )

        val context = createContext()
        assertThrows<FlowFatalException> {
            stage.runFlow(context, TIMEOUT) {}
        }
    }

    @Test
    fun `when the request handler throws a platform exception, this is reported back to the fiber`() {
        val waitingForHandlers = createWaitingForHandlerMap(
            mapOf(
                WaitingFor(SessionConfirmation()) to FlowContinuation.Run(),
                WaitingFor(ExternalEventResponse()) to FlowContinuation.Run()
            )
        )
        val checkpoint = mock<FlowCheckpoint>()
        val secondContext = createContext(waitingFor = null)
        val requestHandlers = createRequestHandlerMap(
            mapOf(
                FlowIORequest.FlowFinished("bar") to Pair(null, secondContext)
            ),
            mapOf(
                fiberOutput to Pair(WaitingFor(ExternalEventResponse()), FlowPlatformException("foo"))
            )
        )
        val fiberOutputs = listOf(flowSuspended, FlowIORequest.FlowFinished("bar"))
        val flowRunner = createFlowRunner(fiberOutputs)
        val stage = FlowExecutionPipelineStage(
            waitingForHandlers,
            requestHandlers,
            flowRunner,
            fiberCache,
            ioRequestTypeConverter
        )

        val context = createContext(checkpoint = checkpoint)
        val outputContext = stage.runFlow(context, TIMEOUT) {}
        assertEquals(secondContext, outputContext)
    }

    private fun verifyInteractions(
        fiberOutputs: List<FlowIORequest<Any?>>
    ) {
        val (suspends, other) = fiberOutputs.partition { it is FlowIORequest.FlowSuspended<*> }
        // Metrics
        verify(metrics, times(fiberOutputs.size)).flowFiberEntered()
        verify(metrics, times(suspends.size)).flowFiberExitedWithSuspension(any())
        verify(metrics, times(other.size)).flowFiberExited()

        // Flow io converter
        verify(ioRequestTypeConverter, times(suspends.size)).convertToActionName(any())

        // Fiber cache
        verify(fiberCache, times(suspends.filter { (it as FlowIORequest.FlowSuspended<*>).cacheableFiber != null }.size))
            .put(any(), any(), any()
        )
        verify(fiberCache, times(other.size)).remove(any<FlowKey>())
    }

    private fun createWaitingForHandlerMap(
        requiredHandlers: Map<WaitingFor, FlowContinuation>
    ): Map<Class<*>, FlowWaitingForHandler<out Any>> {
        return requiredHandlers.map {
            val handler = mock<FlowWaitingForHandler<Any>>()
            whenever(handler.runOrContinue(any(), any())).thenReturn(it.value)
            it.key.value::class.java to handler
        }.toMap()
    }

    private fun createRequestHandlerMap(
        requiredHandlers: Map<FlowIORequest<*>, Pair<WaitingFor?, FlowEventContext<Any>>>,
        failedHandlers: Map<FlowIORequest<*>, Pair<WaitingFor?, Exception>> = mapOf()
    ): Map<Class<out FlowIORequest<*>>, FlowRequestHandler<out FlowIORequest<*>>> {
        val working = requiredHandlers.map {
            val handler = mock<FlowRequestHandler<FlowIORequest<*>>>()
            whenever(handler.getUpdatedWaitingFor(any(), any())).thenReturn(it.value.first)
            whenever(handler.postProcess(any(), any())).thenReturn(it.value.second)
            it.key::class.java to handler
        }.toMap()
        val failing = failedHandlers.map {
            val handler = mock<FlowRequestHandler<FlowIORequest<*>>>()
            whenever(handler.getUpdatedWaitingFor(any(), any())).thenReturn(it.value.first)
            whenever(handler.postProcess(any(), any())).thenThrow(it.value.second)
            it.key::class.java to handler
        }
        return working + failing
    }

    private fun createContext(
        checkpoint: FlowCheckpoint = mock<FlowCheckpoint>(),
        waitingFor: WaitingFor? = WaitingFor(SessionConfirmation())
    ) : FlowEventContext<Any> {
        val context = mock<FlowEventContext<Any>>()
        whenever(context.checkpoint).thenReturn(checkpoint)
        whenever(context.flowMetrics).thenReturn(metrics)
        whenever(checkpoint.waitingFor).thenReturn(waitingFor)
        whenever(checkpoint.flowKey).thenReturn(flowKey)
        return context
    }

    private fun createFlowRunner(
        results: List<FlowIORequest<Any?>>,
        timeoutFlow: Boolean = false,
        fiberFuture: FiberFuture = mock<FiberFuture>()
    ) : FlowRunner {
        val flowRunner = mock<FlowRunner>()
        val future = mock<Future<FlowIORequest<*>>>()
        whenever(fiberFuture.future).thenReturn(future)
        if (!timeoutFlow) {
            whenever(
                future.get(
                    any(),
                    any()
                )
            ).thenAnswer(AdditionalAnswers.returnsElementsOf<FlowIORequest<*>>(results))
        } else {
            whenever(fiberFuture.interruptable).thenReturn(mock<Interruptable>())
            whenever(future.get(any(), any())).thenThrow(TimeoutException("Foo"))
        }
        whenever(flowRunner.runFlow(any(), any())).thenReturn(fiberFuture)
        return flowRunner
    }
}