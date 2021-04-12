package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

interface PubSubProcessor<K, V> {
    val keyClass: Class<K>
    val valueClass: Class<V>

    fun onNext(eventRecord: Record<K, V>)
}

