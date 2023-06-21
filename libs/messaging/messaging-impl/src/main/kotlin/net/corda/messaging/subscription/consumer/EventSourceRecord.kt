package net.corda.messaging.subscription.consumer

import net.corda.messaging.api.records.Record

interface EventSourceRecord<K : Any, E : Any> {
    val partition: Int
    val topic: String
    val offset: Long
    val key: K
    val record: Record<K, E>
}