package net.corda.flow.manager

import net.corda.data.flow.FlowKey
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.records.Record

data class FlowResult(
    val checkpoint: Checkpoint?,
    val events: List<Record<FlowKey, Any>>
)
