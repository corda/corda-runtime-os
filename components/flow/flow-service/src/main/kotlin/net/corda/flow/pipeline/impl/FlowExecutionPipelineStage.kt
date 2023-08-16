package net.corda.flow.pipeline.impl

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.runner.FlowRunner
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class FlowExecutionPipelineStage(
    private val flowWaitingForHandlers: Map<Class<*>, FlowWaitingForHandler<out Any>>,
    private val flowRequestHandlers: Map<Class<out FlowIORequest<*>>, FlowRequestHandler<out FlowIORequest<*>>>,
    private val flowRunner: FlowRunner,
    private val fiberCache: FlowFiberCache
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.name)
    }

    fun runFlow(context: FlowEventContext<Any>) : FlowEventContext<Any> {
        var currentContext = context
        var continuation = flowReady(context)
        while (continuation != FlowContinuation.Continue) {
            val output = executeFlow(currentContext, continuation)
            currentContext = updateContext(output, currentContext)
            continuation = flowReady(currentContext)
        }
        return currentContext
    }

    private fun flowReady(context: FlowEventContext<Any>) : FlowContinuation {
        val waitingFor = context.checkpoint.waitingFor?.value
            ?: throw FlowFatalException("Flow [${context.checkpoint.flowId}] waiting for is null")
        @Suppress("unchecked_cast")
        val handler = flowWaitingForHandlers[waitingFor::class.java] as? FlowWaitingForHandler<Any>
            ?: throw FlowFatalException("${waitingFor::class.qualifiedName} does not have an associated flow status handler")
        return handler.runOrContinue(context, waitingFor)
    }

    private fun executeFlow(context: FlowEventContext<Any>, continuation: FlowContinuation) : FlowIORequest<*> {
        val future = flowRunner.runFlow(context, continuation)

        val fiberResult = try {
            future.future.get(1000, TimeUnit.MILLISECONDS) // TODO: need to get the config
        } catch (e: TimeoutException) {
            logger.warn("Flow execution timeout, Flow marked as failed, interrupt attempted")
            // This works in extremely limited circumstances. The Flow which experienced a timeout will continue to
            // show the status RUNNING. Flows started in the waiting period will end up stuck in the START_REQUESTED
            // state. The biggest benefit to this timeout is the error logging and the fact that waiting here
            // indefinitely is blocking the FlowWorker for all Flows indefinitely too. See CORE-5820.
            future.interruptable.attemptInterrupt()
            FlowIORequest.FlowFailed(e)
        }

        return when (fiberResult) {
            is FlowIORequest.FlowFinished -> {
                fiberCache.remove(context.checkpoint.flowKey)
                context.checkpoint.serializedFiber = ByteBuffer.wrap(byteArrayOf())
                context.flowMetrics.flowFiberExited()
                fiberResult
            }

            is FlowIORequest.FlowSuspended<*> -> {
                fiberResult.cacheableFiber?.let {
                    fiberCache.put(context.checkpoint.flowKey, it)
                }
                context.checkpoint.serializedFiber = fiberResult.fiber
//                context.flowMetrics.flowFiberExitedWithSuspension(
//                    flowIORequestTypeConverter.convertToActionName(flowResult.output)
//                )
                fiberResult.output
            }

            is FlowIORequest.FlowFailed -> {
                fiberCache.remove(context.checkpoint.flowKey)
                context.flowMetrics.flowFiberExited()
                fiberResult
            }

            else -> throw FlowFatalException("Invalid ${FlowIORequest::class.java.simpleName} returned from flow fiber")
        }
    }

    private fun updateContext(output: FlowIORequest<*>, context: FlowEventContext<Any>) : FlowEventContext<Any> {
        // This [uncheckedCast] is required to remove the [out] from the [FlowRequestHandler] that is extracted from the map.
        // The [out] cannot be kept as it leaks onto the [FlowRequestHandler] interface eventually leading to code that cannot compile.
        @Suppress("unchecked_cast")
        val handler = flowRequestHandlers[output::class.java] as? FlowRequestHandler<FlowIORequest<*>>
            ?: throw FlowFatalException("${output::class.qualifiedName} does not have an associated flow request handler")
        context.checkpoint.suspendedOn = output::class.qualifiedName!!
        context.checkpoint.waitingFor = handler.getUpdatedWaitingFor(context, output)
        return handler.postProcess(context, output)
    }
}