package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.FlowEventPipeline

interface FlowEventPipelineFactory {

    fun create(checkpoint: Checkpoint?, event: FlowEvent): FlowEventPipeline
}