package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.FlowEventPipeline

/**
 * The [FlowRequestHandler] interface is implemented by services that process [FlowIORequest]s output by fibers when they suspend.
 *
 * @param T The [FlowIORequest] that the [FlowRequestHandler] handles.
 */
interface FlowRequestHandler<T : FlowIORequest<*>> {

    /**
     * Gets the [Class] of the [FlowIORequest] that the handler accepts.
     */
    val type: Class<T>

    /**
     * Gets the new [WaitingFor] value that the flow's checkpoint should be updated to.
     *
     * @param context The [FlowEventContext] that __should not__ be modified within this processing step.
     * @param request The [FlowIORequest] output from the suspended flow.
     *
     * @return The new [WaitingFor] value.
     */
    fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: T): WaitingFor

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
}
