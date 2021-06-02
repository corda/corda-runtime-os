package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

/**
 * A processor of events from a durable subscription. Consumer processors of events
 * from durable subscriptions should implement this interface.
 */
interface DurableProcessor<K : Any, V : Any> {

    /**
     * Process a list of records and return a list of new records to be produced.
     * @return Records that can be of different key and value types intended to be put on different topics.
     */
    fun onNext(events: List<Record<K, V>>) : List<Record<*, *>>

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on.
     */
    val keyClass: Class<K>
    val valueClass: Class<V>
}

