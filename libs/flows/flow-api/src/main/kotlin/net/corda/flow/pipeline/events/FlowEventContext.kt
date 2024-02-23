package net.corda.flow.pipeline.events

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.pipeline.metrics.FlowMetrics
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.Metadata
import net.corda.messaging.api.records.Record

/**
 * [FlowEventContext] contains information about a received [FlowEvent] and state that should be modified when passed through a
 * [FlowEventPipeline].
 *
 * When a [FlowEventPipeline] completes the [checkpoint] and [outputRecords] of the context will be written to the message bus. Setting the
 * [checkpoint] to null will cause its mapping to be removed from the compacted topic.
 *
 * @param checkpoint The [FlowCheckpoint] of a flow that should be modified by the pipeline.
 * @param inputEvent The received [FlowEvent].
 * @param inputEventPayload The received [FlowEvent.payload].
 * @param isRetryEvent True if this event is being retried.
 * @param outputRecords The [Record]s that should be sent back to the message bus when the pipeline completes.
 * @param T The type of [FlowEvent.payload].
 * @param mdcProperties properties to set the flow fibers MDC with.
 * @param flowMetrics The [FlowMetrics] instance associated with the flow event
 * @param flowTraceContext The [TraceContext] instance associated with the flow event
 * @param metadata Metadata associated with the checkpoint in state storage
 * @param inputEventHash The hash of the original bus input associated with the current [event]. For RPC responses fed back into the
 * pipeline, the hash will be that of the original consumer input from the bus. Used for storing events for replay logic.
 * */
data class FlowEventContext<T>(
    val checkpoint: FlowCheckpoint,
    val inputEvent: FlowEvent,
    var inputEventPayload: T,
    val configs: Map<String, SmartConfig>,
    val flowConfig: SmartConfig,
    var isRetryEvent: Boolean = false,
    val outputRecords: List<Record<*, *>>,
    val sendToDlq: Boolean = false,
    val mdcProperties: Map<String, String>,
    val flowMetrics: FlowMetrics,
    val metadata: Metadata?,
    val inputEventHash: String?,
)
