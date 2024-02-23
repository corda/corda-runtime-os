package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor.State

/**
 * [FlowEventPipelineFactory] creates [FlowEventPipeline]s as part of flow event processing.
 */
@Suppress("LongParameterList")
interface FlowEventPipelineFactory {

    /**
     * Creates a [FlowEventPipeline] instance.
     *
     * @param state The [Checkpoint] and metadata passed through the pipeline.
     * @param event The [FlowEvent] passed through the pipeline.
     * @param config The [SmartConfig] containing the settings used in the pipeline factory.
     * @param mdcProperties properties to set the flow fibers MDC with.
     * @param traceContext the tracing context spanning the pipeline execution.
     * @param eventRecordTimestamp The produced timestamp of the flow event record.
     * @param inputEventHash The hash of the original bus input associated with the current [event]. For RPC responses fed back into the
     * pipeline, the hash will be that of the original consumer input from the bus. Used for storing events for replay logic.
     *
     * @return A new [FlowEventPipeline] instance.
     */
    fun create(
        state: State<Checkpoint>?,
        event: FlowEvent,
        configs: Map<String, SmartConfig>,
        mdcProperties: Map<String, String>,
        eventRecordTimestamp: Long,
        inputEventHash: String?
    ): FlowEventPipeline
}