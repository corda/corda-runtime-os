package net.corda.flow.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.fiber.FlowIORequest

/**
 * [FlowEventPipeline] encapsulates the pipeline steps that are executed when a [FlowEvent] is received by a [FlowEventProcessor].
 */
interface FlowEventPipeline {

    /**
     * The current pipeline processing context
     */
    val context: FlowEventContext<Any>

    /**
     * Performs flow event pre-processing on the pipeline.
     *
     * @return The updated pipeline instance.
     */
    fun eventPreProcessing(): FlowEventPipeline

    /**
     * Runs the pipeline's flow (starts or resumes) if required and waits for it to suspend.
     *
     * @return The updated pipeline instance.
     */
    fun runOrContinue(): FlowEventPipeline

    /**
     * Sets the pipeline's [Checkpoint]'s suspendedOn property.
     *
     * @return The updated pipeline instance.
     */
    fun setCheckpointSuspendedOn(): FlowEventPipeline

    /**
     * Sets the pipeline's [Checkpoint]'s waitingFor property.
     *
     * @return The updated pipeline instance.
     */
    fun setWaitingFor(): FlowEventPipeline

    /**
     * Performs [FlowIORequest] post-processing on the pipeline.
     *
     * @return The updated pipeline instance.
     */
    fun requestPostProcessing(): FlowEventPipeline

    /**
     * Performs post-processing that should always execute.
     *
     * @return The updated pipeline instance.
     */
    fun globalPostProcessing(): FlowEventPipeline
}