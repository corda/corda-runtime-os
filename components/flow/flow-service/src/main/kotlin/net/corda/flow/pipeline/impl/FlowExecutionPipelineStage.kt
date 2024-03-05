package net.corda.flow.pipeline.impl

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.metrics.FlowIORequestTypeConverter
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.messaging.api.exception.CordaMessageAPIConsumerResetException
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Pipeline stage to run user code and update the checkpoint with the output.
 *
 * This class is responsible for deciding whether to run user code and updating the checkpoint object with the outputs
 * of the execution. Once [runFlow] has been called, the user code part of the flow should have been executed as far as
 * is possible with the current data available to it.
 */
internal class FlowExecutionPipelineStage(
    private val flowWaitingForHandlers: Map<Class<*>, FlowWaitingForHandler<out Any>>,
    private val flowRequestHandlers: Map<Class<out FlowIORequest<*>>, FlowRequestHandler<out FlowIORequest<*>>>,
    private val flowRunner: FlowRunner,
    private val fiberCache: FlowFiberCache,
    private val flowIORequestTypeConverter: FlowIORequestTypeConverter
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass.name)
    }

    /**
     * Run the user code and update the checkpoint object with the results of this execution.
     *
     * This class may choose to run the user code multiple times if the required data to do so is available. It should
     * return to the caller once the user code has been executed to the point where more data is required.
     *
     * @param context The current flow context.
     * @param timeout Timeout for an individual flow execution.
     * @param notifyContextUpdate A function to signal intermediate context updates to the caller.
     *                            Useful for error scenarios.
     * @return FlowEventContext Updated context for the flow.
     */
    fun runFlow(
        context: FlowEventContext<Any>,
        timeout: Long,
        notifyContextUpdate: (FlowEventContext<Any>) -> Unit
    ) : FlowEventContext<Any> {
        var currentContext = context
        var continuation = flowReady(currentContext)
        while (continuation != FlowContinuation.Continue) {
            continuation = try {
                val output = executeFlow(currentContext, continuation, timeout)
                currentContext = updateContext(output, currentContext)
                notifyContextUpdate(currentContext)
                flowReady(currentContext)
            } catch (e: FlowPlatformException) {
                FlowContinuation.Error(
                    CordaRuntimeException(
                        e.message, e
                    )
                )
            }
        }
        return currentContext
    }

    private fun flowReady(context: FlowEventContext<Any>) : FlowContinuation {
        // If the waiting for value is null, that indicates that the previous run of the fiber resulted in the flow
        // terminating.
        val waitingFor = context.checkpoint.waitingFor?.value
            ?: return FlowContinuation.Continue
        @Suppress("unchecked_cast")
        val handler = flowWaitingForHandlers[waitingFor::class.java] as? FlowWaitingForHandler<Any>
            ?: throw FlowFatalException("${waitingFor::class.qualifiedName} does not have an associated flow status handler")
        return handler.runOrContinue(context, waitingFor)
    }

    private fun executeFlow(context: FlowEventContext<Any>, continuation: FlowContinuation, timeout: Long) : FlowIORequest<*> {
        context.flowMetrics.flowFiberEntered()
        val future = flowRunner.runFlow(context, continuation)

        val fiberResult = try {
            // The user code may run multiple times as part of the same event processing. This means this timeout will
            // not necessarily guarantee that the processing finishes before the underlying consumer timeout occurs.
            // For now this is a known limitation, but the message patterns should be changed to ensure that the
            // processing step does not block the consumer to the point where it cannot process messages any more.
            future.future.get(timeout, TimeUnit.MILLISECONDS)
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
                context.checkpoint.serializedFiber = fiberResult.fiber
                fiberResult.cacheableFiber?.let {
                    fiberCache.put(context.checkpoint.flowKey, context.checkpoint.suspendCount, it)
                }
                context.flowMetrics.flowFiberExitedWithSuspension(
                    flowIORequestTypeConverter.convertToActionName(fiberResult.output)
                )
                fiberResult.output
            }

            is FlowIORequest.FlowFailed -> {
                fiberCache.remove(context.checkpoint.flowKey)
                context.flowMetrics.flowFiberExited()
                fiberResult
            }

            is FlowIORequest.FlowRetry -> {
                throw CordaMessageAPIConsumerResetException("Flow ${context.checkpoint.flowId} requested a retry")
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