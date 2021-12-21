package net.corda.flow.manager.impl.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.FlowEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * [FlowEventPipelineImpl] encapsulates the pipeline steps that are executed when a [FlowEvent] is received by a [FlowEventProcessor].
 */
interface FlowEventPipeline {
    fun eventPreProcessing(): FlowEventPipeline
    fun runOrContinue(): FlowEventPipeline
    fun setCheckpointSuspendedOn(): FlowEventPipeline
    fun requestPostProcessing(): FlowEventPipeline
    fun eventPostProcessing(): FlowEventPipeline
    fun toStateAndEventResponse(): StateAndEventProcessor.Response<Checkpoint>
}