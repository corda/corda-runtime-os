package net.corda.flow.pipeline.impl

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.metrics.FlowIORequestTypeConverter
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.handlers.events.FlowEventHandler
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.utilities.trace
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory

/**
 * [FlowEventPipelineImpl] encapsulates the pipeline steps that are executed when a [FlowEvent] is received by a [FlowEventProcessor].
 *
 * @param flowEventHandlers Map of available [FlowEventHandler]s where one is used for processing incoming events.
 * @param flowWaitingForHandlers Map of available [FlowWaitingForHandler]s, where one is used for the current waitingFor state.
 * @param flowRequestHandlers Map of available [FlowRequestHandler]s, where one is used for post-processing after a flow suspends.
 * @param flowRunner The [FlowRunner] that is used to start or resume a flow's fiber.
 * @param flowGlobalPostProcessor The [FlowGlobalPostProcessor] applied to all events .
 * @param context The [FlowEventContext] that should be modified by the pipeline steps.
 * @param virtualNodeInfoReadService The [VirtualNodeInfoReadService] is responsible for reading virtual node information.
 * @param output The [FlowIORequest] that is output by a flow's fiber when it suspends.
 */
@Suppress("LongParameterList")
class FlowEventPipelineImpl(
    private val flowEventHandlers: Map<Class<*>, FlowEventHandler<out Any>>,
    private val flowWaitingForHandlers: Map<Class<*>, FlowWaitingForHandler<out Any>>,
    private val flowRequestHandlers: Map<Class<out FlowIORequest<*>>, FlowRequestHandler<out FlowIORequest<*>>>,
    private val flowRunner: FlowRunner,
    private val flowGlobalPostProcessor: FlowGlobalPostProcessor,
    override var context: FlowEventContext<Any>,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val flowFiberCache: FlowFiberCache,
    private val flowIORequestTypeConverter: FlowIORequestTypeConverter,
    private var output: FlowIORequest<*>? = null
) : FlowEventPipeline {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun eventPreProcessing(): FlowEventPipelineImpl {
        log.trace { "Preprocessing of ${context.inputEventPayload::class.qualifiedName}" }

        /**
         * If the checkpoint is in a retry step and we receive a Wakeup then we
         * should re-write the event the pipeline should process the event to be retried, in place of the default
         * wakeup behavior
         */
        val updatedContext = if (context.checkpoint.inRetryState && context.inputEventPayload is Wakeup) {
            log.debug(
                "Flow is in retry state, using retry event " +
                        "${context.checkpoint.retryEvent.payload::class.qualifiedName} for the pipeline processing."
            )
            context.copy(
                inputEvent = context.checkpoint.retryEvent,
                inputEventPayload = context.checkpoint.retryEvent.payload,
                isRetryEvent = true
            )
        } else {
            context
        }

        val handler = getFlowEventHandler(updatedContext.inputEvent)

        context = handler.preProcess(updatedContext)

        // For now, we do this here as we need to be sure the flow start context exists, as for a
        // start flow event it won't exist until we have run the preProcess() for the start flow
        // event handler
        context.flowMetrics.flowEventReceived()

        return this
    }

    override fun virtualNodeFlowOperationalChecks(): FlowEventPipeline {
        if (!context.checkpoint.doesExist) {
            log.warn("Could not perform flow operational validation as the checkpoint does not exist.")
            return this
        }
        val holdingIdentity = context.checkpoint.holdingIdentity
        val virtualNode = virtualNodeInfoReadService.get(holdingIdentity)
            ?: throw FlowTransientException("Failed to find the virtual node info for holder " +
                    "'HoldingIdentity(x500Name=${holdingIdentity.x500Name}, groupId=${holdingIdentity.groupId})'")

        if (virtualNode.flowOperationalStatus == OperationalStatus.INACTIVE) {
            throw FlowMarkedForKillException("Flow operational status is ${virtualNode.flowOperationalStatus.name}")
        }
        return this
    }

    override fun runOrContinue(timeoutMilliseconds: Long): FlowEventPipelineImpl {
        val waitingFor = context.checkpoint.waitingFor?.value
            ?: throw FlowFatalException("Flow [${context.checkpoint.flowId}] waiting for is null")

        val handler = getFlowWaitingForHandler(waitingFor)

        log.trace { "Run or continue when flow is waiting for $waitingFor" }

        return when (val outcome = handler.runOrContinue(context, waitingFor)) {
            is FlowContinuation.Run, is FlowContinuation.Error -> {
                updateContextFromFlowExecution(outcome, timeoutMilliseconds)
            }
            is FlowContinuation.Continue -> this
        }
    }

    override fun setCheckpointSuspendedOn(): FlowEventPipelineImpl {
        // If the flow fiber did not run or resume then there is no `suspendedOn` to change to.
        output?.let {
            context.checkpoint.suspendedOn = it::class.qualifiedName!!
        }
        return this
    }

    override fun setWaitingFor(): FlowEventPipelineImpl {
        output?.let {
            val waitingFor = getFlowRequestHandler(it).getUpdatedWaitingFor(context, it)
            context.checkpoint.waitingFor = waitingFor
        }
        return this
    }

    override fun requestPostProcessing(): FlowEventPipelineImpl {
        output?.let {
            log.trace { "Postprocessing of $output" }
            context = getFlowRequestHandler(it).postProcess(context, it)
        }
        return this
    }

    override fun globalPostProcessing(): FlowEventPipelineImpl {
        context = flowGlobalPostProcessor.postProcess(context)
        return this
    }

    private fun getFlowEventHandler(event: FlowEvent): FlowEventHandler<Any> {
        @Suppress("unchecked_cast")
        return flowEventHandlers[event.payload::class.java] as? FlowEventHandler<Any>
            ?: throw FlowFatalException("${event.payload::class.java.name} does not have an associated flow event handler")
    }

    private fun getFlowWaitingForHandler(waitingFor: Any): FlowWaitingForHandler<Any> {
        // This [uncheckedCast] is required to pass the [waitingFor] into the returned [FlowWaitingForHandler] further in the pipeline.
        @Suppress("unchecked_cast")
        return flowWaitingForHandlers[waitingFor::class.java] as? FlowWaitingForHandler<Any>
            ?: throw FlowFatalException("${waitingFor::class.qualifiedName} does not have an associated flow status handler")
    }

    private fun getFlowRequestHandler(request: FlowIORequest<*>): FlowRequestHandler<FlowIORequest<*>> {
        // This [uncheckedCast] is required to remove the [out] from the [FlowRequestHandler] that is extracted from the map.
        // The [out] cannot be kept as it leaks onto the [FlowRequestHandler] interface eventually leading to code that cannot compile.
        @Suppress("unchecked_cast")
        return flowRequestHandlers[request::class.java] as? FlowRequestHandler<FlowIORequest<*>>
            ?: throw FlowFatalException("${request::class.qualifiedName} does not have an associated flow request handler")
    }

    private fun updateContextFromFlowExecution(
        outcome: FlowContinuation,
        timeoutMilliseconds: Long
    ): FlowEventPipelineImpl {
        context.flowMetrics.flowFiberEntered()
        val flowResultFuture = flowRunner.runFlow(
            context,
            outcome
        )

        val flowResult = try {
            flowResultFuture.future.get(timeoutMilliseconds, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            log.warn("Flow execution timeout, Flow marked as failed, interrupt attempted")
            // This works in extremely limited circumstances. The Flow which experienced a timeout will continue to
            // show the status RUNNING. Flows started in the waiting period will end up stuck in the START_REQUESTED
            // state. The biggest benefit to this timeout is the error logging and the fact that waiting here
            // indefinitely is blocking the FlowWorker for all Flows indefinitely too. See CORE-5820.
            flowResultFuture.interruptable.attemptInterrupt()
            FlowIORequest.FlowFailed(e)
        }
        when (flowResult) {
            is FlowIORequest.FlowFinished -> {
                flowFiberCache.remove(context.checkpoint.flowKey)
                context.checkpoint.serializedFiber = ByteBuffer.wrap(byteArrayOf())
                output = flowResult
                context.flowMetrics.flowFiberExited()
            }

            is FlowIORequest.FlowSuspended<*> -> {
                flowResult.cacheableFiber?.let {
                    flowFiberCache.put(context.checkpoint.flowKey, it)
                }
                context.checkpoint.serializedFiber = flowResult.fiber
                output = flowResult.output
                context.flowMetrics.flowFiberExitedWithSuspension(
                    flowIORequestTypeConverter.convertToActionName(flowResult.output)
                )
            }

            is FlowIORequest.FlowFailed -> {
                flowFiberCache.remove(context.checkpoint.flowKey)
                output = flowResult
                context.flowMetrics.flowFiberExited()
            }

            else -> throw FlowFatalException("Invalid ${FlowIORequest::class.java.simpleName} returned from flow fiber")
        }
        return this
    }
}
