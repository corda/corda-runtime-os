package net.cordax.flowworker.api.processor

import net.cordax.flowworker.api.records.Record

interface DurableProcessor<K, V> {
    val keyClass: Class<K>
    val valueClass: Class<V>

    fun onNext(event: Record<K, V>) : List<Record<*, *>>
}

