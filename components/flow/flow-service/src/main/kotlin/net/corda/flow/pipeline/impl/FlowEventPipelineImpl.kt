package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.handlers.events.FlowEventHandler
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import java.nio.ByteBuffer

/**
 * [FlowEventPipelineImpl] encapsulates the pipeline steps that are executed when a [FlowEvent] is received by a [FlowEventProcessor].
 *
 * @param flowEventHandler The [FlowEventHandler] that is used for event processing pipeline steps.
 * @param flowRequestHandlers The registered [FlowRequestHandler]s, where one is used to request post-processing after a flow suspends.
 * @param flowRunner The [FlowRunner] that is used to start or resume a flow's fiber.
 * @param context The [FlowEventContext] that should be modified by the pipeline steps.
 * @param output The [FlowIORequest] that is output by a flow's fiber when it suspends.
 */
data class FlowEventPipelineImpl(
    val flowEventHandler: FlowEventHandler<Any>,
    val flowWaitingForHandlers: Map<Class<*>, FlowWaitingForHandler<out Any>>,
    val flowRequestHandlers: Map<Class<out FlowIORequest<*>>, FlowRequestHandler<out FlowIORequest<*>>>,
    val flowRunner: FlowRunner,
    val flowGlobalPostProcessor: FlowGlobalPostProcessor,
    val context: FlowEventContext<Any>,
    val output: FlowIORequest<*>? = null
) : FlowEventPipeline {

    private companion object {
        val log = contextLogger()
    }

    override fun eventPreProcessing(): FlowEventPipelineImpl {
        log.info("Preprocessing of ${context.inputEventPayload::class.qualifiedName} using ${flowEventHandler::class.qualifiedName}")
        return copy(context = flowEventHandler.preProcess(context))
    }

    override fun runOrContinue(): FlowEventPipelineImpl {
        val waitingFor = context.checkpoint?.flowState?.waitingFor?.value
            ?: throw FlowProcessingException("Flow [${context.checkpoint?.flowKey?.flowId}] waiting for is null")

        val handler = getFlowWaitingForHandler(waitingFor)

        log.info("Run or continue using ${handler::class.java.name} when flow is waiting for $waitingFor")

        return when (val outcome = handler.runOrContinue(context, waitingFor)) {
            is FlowContinuation.Run, is FlowContinuation.Error -> {
                updateContextFromFlowExecution(outcome)
            }
            is FlowContinuation.Continue -> this
        }
    }

    override fun setCheckpointSuspendedOn(): FlowEventPipelineImpl {
        // If the flow fiber did not run or resume then there is no `suspendedOn` to change to.
        output?.let {
            requireCheckpoint(context) { "The flow must have a checkpoint after suspending" }
                .flowState
                .suspendedOn = it::class.qualifiedName
        }
        return this
    }

    override fun setWaitingFor(): FlowEventPipelineImpl {
        output?.let {
            val waitingFor = getFlowRequestHandler(it).getUpdatedWaitingFor(context, it)
            requireCheckpoint(context) { "The flow must have a checkpoint after suspending" }
                .flowState
                .waitingFor = waitingFor
        }
        return this
    }

    override fun requestPostProcessing(): FlowEventPipelineImpl {
        return output?.let {
            log.info("Postprocessing of $output")
            copy(context = getFlowRequestHandler(it).postProcess(context, it))
        } ?: this
    }

    override fun globalPostProcessing(): FlowEventPipelineImpl {
        return copy(context = flowGlobalPostProcessor.postProcess(context))
    }

    override fun toStateAndEventResponse(): StateAndEventProcessor.Response<Checkpoint> {
        log.info("Sending output records to message bus: ${context.outputRecords}")
        return StateAndEventProcessor.Response(context.checkpoint, context.outputRecords)
    }

    private fun getFlowWaitingForHandler(waitingFor: Any): FlowWaitingForHandler<Any> {
        // This [uncheckedCast] is required to pass the [waitingFor] into the returned [FlowWaitingForHandler] further in the pipeline.
        return uncheckedCast(flowWaitingForHandlers[waitingFor::class.java])
            ?: throw FlowProcessingException("${waitingFor::class.qualifiedName} does not have an associated flow waiting for handler")
    }

    private fun getFlowRequestHandler(request: FlowIORequest<*>): FlowRequestHandler<FlowIORequest<*>> {
        // This [uncheckedCast] is required to remove the [out] from the [FlowRequestHandler] that is extracted from the map.
        // The [out] cannot be kept as it leaks onto the [FlowRequestHandler] interface eventually leading to code that cannot compile.
        return uncheckedCast(flowRequestHandlers[request::class.java])
            ?: throw FlowProcessingException("${request::class.qualifiedName} does not have an associated flow request handler")
    }

    private inline fun requireCheckpoint(context: FlowEventContext<Any>, message: () -> String): Checkpoint {
        return context.checkpoint ?: message().let {
            log.error("$it. Context: $context")
            throw FlowProcessingException(it)
        }
    }

    private fun updateContextFromFlowExecution(outcome: FlowContinuation): FlowEventPipelineImpl {
        val flowResultFuture = flowRunner.runFlow(
            context,
            outcome
        )

        /*
        Need to think about a timeout for the get(), what do we do if a flow does not complete?
        */
        return when (val flowResult = flowResultFuture.get()) {
            is FlowIORequest.FlowFinished -> {
                context.checkpoint!!.fiber = ByteBuffer.wrap(byteArrayOf())
                copy(output = flowResult)
            }
            is FlowIORequest.FlowSuspended<*> -> {
                context.checkpoint!!.fiber = flowResult.fiber
                copy(output = flowResult.output)
            }
            is FlowIORequest.FlowFailed -> {
                TODO("Flow Failure Path TBD")
            }
            else -> throw FlowProcessingException("Invalid ${FlowIORequest::class.java.simpleName} returned from flow fiber")
        }
    }
}