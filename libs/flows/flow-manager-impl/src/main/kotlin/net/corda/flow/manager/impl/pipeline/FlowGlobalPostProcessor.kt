package net.corda.flow.manager.impl.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.flow.manager.impl.FlowEventContext

interface FlowGlobalPostProcessor {

    /**
     * Performs post-processing when receiving a [FlowEvent].
     *
     * Post-processing is executed as the last step and occurs after a flow has suspended.
     *
     * This step will still execute even if [runOrContinue] returned [FlowContinuation.Continue].
     *
     * @param context The [FlowEventContext] that should be modified within this processing step.
     *
     * @return The modified [FlowEventContext].
     */
    fun postProcess(context: FlowEventContext<Any>): FlowEventContext<Any>
}