package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

/**
 * A processor of events from a durable subscription.
 */
interface DurableProcessor<K, V> {

    /**
     * Process an [event] record and produce a list of new records.
     * @return records that can be of different key and value types intended to be put on different topics.
     */
    fun onNext(event: Record<K, V>) : List<Record<*, *>>

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on
     */
    val keyClass: Class<K>
    val valueClass: Class<V>
}

