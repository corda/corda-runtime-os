package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.FlowEventPipeline

/**
 * [FlowEventPipelineFactory] creates [FlowEventPipeline]s as part of flow event processing.
 */
interface FlowEventPipelineFactory {

    /**
     * Creates a [FlowEventPipeline] instance.
     *
     * @param checkpoint The [Checkpoint] passed through the pipeline.
     * @param event The [FlowEvent] passed through the pipeline.
     *
     * @return A new [FlowEventPipeline] instance.
     */
    fun create(checkpoint: Checkpoint?, event: FlowEvent): FlowEventPipeline
}