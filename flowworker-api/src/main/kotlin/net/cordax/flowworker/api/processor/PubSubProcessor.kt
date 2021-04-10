package net.cordax.flowworker.api.processor

import net.cordax.flowworker.api.records.Record

interface PubSubProcessor<K, V> {
    val keyClass: Class<K>
    val valueClass: Class<V>

    fun onNext(eventRecord: Record<K, V>)
}

