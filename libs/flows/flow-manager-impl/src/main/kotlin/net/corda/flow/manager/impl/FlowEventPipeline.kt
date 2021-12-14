package net.corda.flow.manager.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.processor.StateAndEventProcessor

interface FlowEventPipeline {

    fun start(checkpoint: Checkpoint?, event: FlowEvent) : FlowEventPipelineContext

    fun eventPreProcessing(pipelineContext: FlowEventPipelineContext): FlowEventPipelineContext

    fun runOrContinue(pipelineContext: FlowEventPipelineContext): FlowEventPipelineContext

    fun setCheckpointSuspendedOn(pipelineContext: FlowEventPipelineContext): FlowEventPipelineContext

    fun requestPostProcessing(pipelineContext: FlowEventPipelineContext): FlowEventPipelineContext

    fun eventPostProcessing(pipelineContext: FlowEventPipelineContext): FlowEventPipelineContext

    fun toStateAndEventResponse(pipelineContext: FlowEventPipelineContext): StateAndEventProcessor.Response<Checkpoint>
}