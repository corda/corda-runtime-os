package net.corda.flow.pipeline

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.schema.configuration.FlowConfig.SESSION_MISSING_COUNTERPARTY_TIMEOUT_WINDOW

/**
 * [FlowGlobalPostProcessor] performs post-processing that should always execute as part of the [FlowEventPipeline].
 *
 * This post-processing is executed as the last step in the pipeline and occurs after a flow has suspended.
 *
 * This step will still execute even if [FlowEventPipeline.runOrContinue] returned [FlowContinuation.Continue].
 */
interface FlowGlobalPostProcessor {

    /**
     * Performs post-processing.
     *
     * @param context The [FlowEventContext] that should be modified within this processing step.
     *
     * @return The modified [FlowEventContext].
     *
     * @throws [FlowPlatformException] if session counterparties can't be resolved within [SESSION_MISSING_COUNTERPARTY_TIMEOUT_WINDOW]
     */
    fun postProcess(context: FlowEventContext<Any>): FlowEventContext<Any>
}
