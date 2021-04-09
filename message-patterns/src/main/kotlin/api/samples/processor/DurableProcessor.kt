package api.samples.processor

import api.samples.records.Record

interface DurableProcessor<K, V> {
    val keyClass: Class<K>
    val valueClass: Class<V>

    fun onNext(event: Record<K, V>) : List<Record<*, *>>
}

