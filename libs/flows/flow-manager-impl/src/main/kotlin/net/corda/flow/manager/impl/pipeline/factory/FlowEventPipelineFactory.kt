package net.corda.flow.manager.impl.pipeline.factory

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.impl.pipeline.FlowEventPipeline

interface FlowEventPipelineFactory {

    fun create(checkpoint: Checkpoint?, event: FlowEvent): FlowEventPipeline
}