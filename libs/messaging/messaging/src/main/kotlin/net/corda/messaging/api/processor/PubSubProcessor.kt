package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

/**
 * This interface defines a processor of events from a non durable [PubSubSubscription] with a feed key
 * of type [K] and value of type [V].
 *
 * If you want to receive events from a from [PubSubSubscription] you should implement this interface.
 *
 * NOTE: Any exception thrown by the processor which isn't [CordaIntermittentException] will result in a
 * [CordaFatalException] and will cause the subscription to close
 */
interface PubSubProcessor<K : Any, V : Any> {

    /**
     * Implement this method to receive the next [event] record from the subscription feed.
     */
    fun onNext(event: Record<K, V>)

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on.
     *
     * Override these values with the classes for [K] and [V] for your specific subscription.
     */
    val keyClass: Class<K>
    val valueClass: Class<V>
}

