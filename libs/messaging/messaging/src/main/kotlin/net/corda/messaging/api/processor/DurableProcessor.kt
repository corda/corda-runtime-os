package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

/**
 * This interface defines a processor of events from a [DurableSubscription] on a feed with keys of type [K],
 * values of type [V].
 *
 * If you want to receive updates from a from [DurableSubscription] you should implement this interface.
 *
 * NOTE: Any exception thrown by the processor which isn't [CordaIntermittentException] will result in a
 * [CordaFatalException] and will cause the subscription to close
 */
interface DurableProcessor<K : Any, V : Any> {

    /**
     * Implement this method to receive a list of updates from the subscription feed.
     *
     * @param events the list of incoming events to process.
     * @return any events to be published by the subscription in response to the incoming ones.
     *
     * Output events can be of different key and value types intended to be put on different topics.
     *
     * NOTE: The returned events will be published and the processed events will be consumed atomically as a
     * single transaction.
     */
    fun onNext(events: List<Record<K, V>>): List<Record<*, *>>

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on.
     *
     * Override these values with the classes for [K] and [V] for your specific subscription.
     */
    val keyClass: Class<K>
    val valueClass: Class<V>
}
