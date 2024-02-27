package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.records.Record

data class EventProcessingInput<K: Any, E: Any>(
    val key: K,
    val records: List<Record<K, E>>,
    val state: State?
)