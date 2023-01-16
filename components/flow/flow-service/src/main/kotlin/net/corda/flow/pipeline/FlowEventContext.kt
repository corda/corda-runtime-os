package net.corda.flow.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
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
 * @param outputRecords The [Record]s that should be sent back to the message bus when the pipeline completes.
 * @param T The type of [FlowEvent.payload].
 * @param mdcProperties properties to set the flow fibers MDC with.
 * @param flowTerminatedContext The context of why this flow was termianted, if not null.
 */
data class FlowEventContext<T>(
    val checkpoint: FlowCheckpoint,
    val inputEvent: FlowEvent,
    var inputEventPayload: T,
    val config: SmartConfig,
    val outputRecords: List<Record<*, *>>,
    val sendToDlq: Boolean = false,
    val mdcProperties: Map<String, String>,
    var flowTerminatedContext: FlowTerminatedContext? = null
) {
    /**
     * [FlowTerminatedContext] contains context of the termination of the processing of a flow event.
     */
    data class FlowTerminatedContext(
        /**
         * The termination status of the processing of this flow event.
         */
        val terminationStatus: TerminationStatus,
        /**
         * Additional details about the termination of this flow event.
         */
        val details: Map<String, String>? = null
    ) {
        enum class TerminationStatus {
            /**
             * Indicates to the flow context that this flow should be killed, all events accrued through prior processing steps will be
             * discarded, the flow status will be marked as 'KILLED', the checkpoint will be deleted.
             */
            TO_BE_KILLED
        }
    }
}