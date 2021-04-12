package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

interface DurableProcessor<K, V> {
    val keyClass: Class<K>
    val valueClass: Class<V>

    fun onNext(event: Record<K, V>) : List<Record<*, *>>
}

