package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

/**
 * A processor of events from a pub sub non durable subscription. Consumer processors of events.
 * from PubSub subscriptions should implement this interface.
 */
interface PubSubProcessor<K : Any, V : Any> {

    /**
     * Process an [event] record and produce a list of new records.
     * @return Records that can be of different key and value types intended to be put on different topics.
     */
    fun onNext(event: Record<K, V>)

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on.
     */
    val keyClass: Class<K>
    val valueClass: Class<V>
}

