package net.corda.flow.manager

import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.event.FlowEvent
import net.corda.messaging.api.records.Record

data class FlowEventContext<T>(
    val checkpoint: Checkpoint?,
    val inputEvent: FlowEvent,
    val inputEventPayload: T,
    val outputRecords: List<Record<*, *>>,
)