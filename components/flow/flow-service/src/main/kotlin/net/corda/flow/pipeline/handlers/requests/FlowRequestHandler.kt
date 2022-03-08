package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.FlowProcessingException

/**
 * The [FlowRequestHandler] interface is implemented by services that process [FlowIORequest]s output by [FlowFiber]s when they suspend.
 *
 * @param T The [FlowIORequest] that the [FlowRequestHandler] handles.
 */
interface FlowRequestHandler<T : FlowIORequest<*>> {

    /**
     * Gets the [Class] of the [FlowIORequest] that the handler accepts.
     */
    val type: Class<T>

    /**
     * Performs post-processing on a [FlowIORequest].
     *
     * Post-processing is executed after a flow has suspended.
     *
     * This step does not execute as part of the [FlowEventPipeline] if the flow did not run.
     *
     * @param context The [FlowEventContext] that should be modified within this processing step.
     *
     * @return The modified [FlowEventContext].
     */
    fun postProcess(context: FlowEventContext<Any>, request: T): FlowEventContext<Any>

    fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: T): WaitingFor
}

/**
 * Throws a [FlowProcessingException] if the passed in [context]'s checkpoint is null.
 *
 * @param context The [FlowEventContext] with a possibly null [Checkpoint].
 *
 * @return A non-null [Checkpoint].
 *
 * @throws FlowProcessingException if the passed in [Checkpoint] is null.
 */
fun FlowRequestHandler<*>.requireCheckpoint(context: FlowEventContext<*>): Checkpoint {
    return context.checkpoint ?: throw FlowProcessingException("${this::class.java.name} requires a non-null checkpoint as input")
}