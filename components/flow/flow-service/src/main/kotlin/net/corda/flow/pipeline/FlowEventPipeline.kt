package net.corda.flow.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * [FlowEventPipeline] encapsulates the pipeline steps that are executed when a [FlowEvent] is received by a [FlowEventProcessor].
 */
interface FlowEventPipeline {

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
     * Sets the pipeline's [Checkpoint]'s [StateMachineState.suspendedOn] property.
     *
     * @return The updated pipeline instance.
     */
    fun setCheckpointSuspendedOn(): FlowEventPipeline

    /**
     * Sets the pipeline's [Checkpoint]'s [StateMachineState.waitingFor] property.
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

    /**
     * Extracts the pipelines [Checkpoint] and output events and returns them.
     *
     * @return A [StateAndEventProcessor.Response] containing the updated [Checkpoint] and events to send to the message bus.
     */
    fun toStateAndEventResponse(): StateAndEventProcessor.Response<Checkpoint>
}