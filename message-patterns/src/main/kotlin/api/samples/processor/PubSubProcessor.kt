package api.samples.processor

import api.samples.records.Record

interface PubSubProcessor<K, V> {
    val keyClass: Class<K>
    val valueClass: Class<V>

    fun onNext(eventRecord: Record<K, V>)
}

