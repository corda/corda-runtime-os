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
     * Used to receive the initial state of the topic when a subscription starts.  Should only be
     * called once and should be called _before_ [onNext].
     *
     * @param currentData the up-to-date state of events for the topic
     */
    fun onFirst(currentData: Map<K, V>)

    /**
     * Called when an update occurs for the subscription.  This will be called when a single event updates
     * the topic, as opposed to [onFirst] which will be the initial state.
     *
     * @param event the specific record update that triggered this call.
     *          - note that for removal of a value from the topic this will result in [V] being null
     * @param currentData the up-to-date state of events for the topic
     */
    fun onNext(event: Record<K, V?>, currentData: Map<K, V>)
}
