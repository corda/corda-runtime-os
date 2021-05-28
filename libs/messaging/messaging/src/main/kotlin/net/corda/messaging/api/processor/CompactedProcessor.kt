package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

/**
 * This interface defines the callback from a compacted subscription.
 * Subscribers are expected to implement this interface to receive the initial compacted
 * snapshot as well as any further updates.
 *
 * The first update from a [CompactedProcessor] will be guaranteed to be an up-to-date
 * representation of the values in the topic (ie, a snapshot).  Further updates will be sent as they occur
 * in a manner similar to [PubSubProcessor] updates.
 */
interface CompactedProcessor<K : Any, V : Any> {

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on.
     */
    val keyClass: Class<K>
    val valueClass: Class<V>

    /**
     * Used to receive the initial state of the topic when a subscription starts or reconnects. Will be called
     * _before_ [onNext], after a connection or reconnection.
     *
     * @param currentData the up-to-date state of events for the topic
     */
    fun onSnapshot(currentData: Map<K, V>)

    /**
     * Called when an update occurs for the subscription.  This will be called when a single event updates
     * the topic, as opposed to [onSnapshot] which will be the initial state.
     *
     * @param oldValue the previous value for the given key in [newRecord].  Will be null if there was no previous
     * value
     * @param newRecord the specific record update that triggered this call.
     *          - note that for removal of a value from the topic this will result in [V] being null
     * @param currentData the up-to-date state of events for the topic
     */
    fun onNext(newRecord: Record<K, V>, oldValue: V?, currentData: Map<K, V>)
}
