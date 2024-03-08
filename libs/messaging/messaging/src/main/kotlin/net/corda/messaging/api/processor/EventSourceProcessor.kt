package net.corda.messaging.api.processor

import net.corda.messaging.api.records.EventLogRecord

/**
 * This interface defines a processor of events from a [EventSourceSubscription] on a feed with keys of type [K],
 * values of type [V].
 * Represents a processor of records from a (partitioned) event log.
 *
 * If you want to receive updates from an [EventSourceSubscription] you should implement this interface.
 *
 * NOTE: Any exception thrown by the processor which isn't [CordaIntermittentException] will result in a
 * [CordaFatalException] and will cause the subscription to close
 */
interface EventSourceProcessor<K : Any, V : Any> {

    /**
     * Implement this method to receive a list of event records from a source topic.
     *
     * @param events a list of [EventLogRecord]s
     *
     */
    fun onNext(events: List<EventLogRecord<K, V>>)

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on.
     *
     * Override these values with the classes for [K] and [V] for your specific subscription.
     */
    val keyClass: Class<K>
    val valueClass: Class<V>
}