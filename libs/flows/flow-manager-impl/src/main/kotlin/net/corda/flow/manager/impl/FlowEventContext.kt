package net.corda.flow.manager.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.records.Record

/**
 * [FlowEventContext] contains information about a received [FlowEvent] and state that should be modified when passed through a
 * [FlowEventPipeline].
 *
 * When a [FlowEventPipeline] completes the [checkpoint] and [outputRecords] of the context will be written to the message bus. Setting the
 * [checkpoint] to null will cause its mapping to be removed from the compacted topic.
 *
 * @param checkpoint The [Checkpoint] of a flow that should be modified by the pipeline.
 * @param inputEvent The received [FlowEvent].
 * @param inputEventPayload The received [FlowEvent.payload].
 * @param outputRecords The [Record]s that should be sent back to the message bus when the pipeline completes.
 * @param T The type of [FlowEvent.payload].
 */
data class FlowEventContext<T>(
    val checkpoint: Checkpoint?,
    val inputEvent: FlowEvent,
    val inputEventPayload: T,
    val outputRecords: List<Record<*, *>>,
)


