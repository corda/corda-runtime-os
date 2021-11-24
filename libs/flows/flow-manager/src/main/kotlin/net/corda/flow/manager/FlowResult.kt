package net.corda.flow.manager

import net.corda.data.flow.Checkpoint
import net.corda.messaging.api.records.Record

data class FlowResult(
    val checkpoint: Checkpoint?,
    val events: List<Record<Any, Any>>
)
