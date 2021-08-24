package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

/**
 * This interface defines a processor of events from a compacted subscription on a feed with keys of type [K] and
 * values of type [V].
 *
 * If you want to receive events from a from [CompactedSubscription] you should implement this interface.
 *
 * Subscribers will receive the initial compacted snapshot as well as any further updates.
 *
 * The first update from a [CompactedProcessor] will be guaranteed to be an up-to-date
 * representation of the values in the topic (ie, a snapshot).  Further updates will be sent as they occur
 * in a manner similar to [PubSubProcessor] updates.
 *
 * NOTE: Any exception thrown by the processor which isn't [CordaIntermittentException] will result in a
 * [CordaFatalException] and will cause the subscription to close
 */
interface CompactedProcessor<K : Any, V : Any> {

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on.
     *
     * Override these values with the classes for [K] and [V] for your specific subscription.
     */
    val keyClass: Class<K>
    val valueClass: Class<V>

    /**
     * Used to receive the initial state of the topic when a subscription starts or reconnects. Upon initial
     * connection or reconnection, there will be an initial invocation of this method followed by invocations of
     * `onNext`.
     *
     * @param currentData the up-to-date state of events for the topic
     */
    fun onSnapshot(currentData: Map<K, V>)

    /**
     * Called when an update occurs for the subscription.  This will be called when a single event updates
     * the topic, as opposed to [onSnapshot] which will be the initial state.
     *
     * @param newRecord the specific record update that triggered this call.
     *          - note that for removal of a value from the topic this will result in the value [V] of [newRecord]
     *          being null
     * @param oldValue the previous value for the given key in [newRecord].  Will be null if there was no previous
     * value.
     * @param currentData the up-to-date state of events for the topic
     */
    fun onNext(newRecord: Record<K, V>, oldValue: V?, currentData: Map<K, V>)
}
