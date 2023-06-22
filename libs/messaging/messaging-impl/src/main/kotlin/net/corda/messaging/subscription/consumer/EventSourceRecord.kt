package net.corda.messaging.subscription.consumer

import net.corda.messaging.api.records.Record

data class EventSourceRecord<K : Any, E : Any>(
    val partition: Int,
    val offset: Long,
    val safeMinOffset:Long,
    val record: Record<K, E>
)
