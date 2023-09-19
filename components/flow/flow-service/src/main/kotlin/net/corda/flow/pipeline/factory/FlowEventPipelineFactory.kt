package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.libs.configuration.SmartConfig
import net.corda.tracing.TraceContext

/**
 * [FlowEventPipelineFactory] creates [FlowEventPipeline]s as part of flow event processing.
 */
@Suppress("LongParameterList")
interface FlowEventPipelineFactory {

    /**
     * Creates a [FlowEventPipeline] instance.
     *
     * @param checkpoint The [Checkpoint] passed through the pipeline.
     * @param event The [FlowEvent] passed through the pipeline.
     * @param config The [SmartConfig] containing the settings used in the pipeline factory.
     * @param mdcProperties properties to set the flow fibers MDC with.
     * @param traceContext the tracing context spanning the pipeline execution.
     * @param eventRecordTimestamp The produced timestamp of the flow event record.
     *
     * @return A new [FlowEventPipeline] instance.
     */
    fun create(
        checkpoint: Checkpoint?,
        event: FlowEvent,
        configs: Map<String, SmartConfig>,
        mdcProperties: Map<String, String>,
        traceContext: TraceContext,
        eventRecordTimestamp: Long
    ): FlowEventPipeline
}