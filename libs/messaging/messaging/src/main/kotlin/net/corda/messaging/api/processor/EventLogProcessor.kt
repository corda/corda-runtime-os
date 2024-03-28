package net.corda.messaging.api.processor

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record

/**
 * This interface defines a processor of events from a [EventLogSubscription] on a feed with keys of type [K],
 * values of type [V].
 * Represents a processor of records from a (partitioned) event log.
 *
 * If you want to receive updates from an [EventLogSubscription] you should implement this interface.
 *
 * NOTE: Any exception thrown by the processor which isn't [CordaIntermittentException] will result in a
 * [CordaFatalException] and will cause the subscription to close
 */
interface EventLogProcessor<K : Any, V : Any> {

    /**
     * Implement this method to receive a list of event log updates from the subscription feed.
     *
     * @param events a list of [EventLogRecord]s
     * @return a list of new [Record]s to be published, when needed.
     *
     * Output events can be of different key and value types intended to be put on different topics.
     * NOTE: The returned events will be published and the processed events will be consumed atomically as a single transaction.
     */
    fun onNext(events: List<EventLogRecord<K, V>>) : List<Record<*, *>>

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on.
     *
     * Override these values with the classes for [K] and [V] for your specific subscription.
     */
    val keyClass: Class<K>
    val valueClass: Class<V>
}


