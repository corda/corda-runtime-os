package net.corda.messaging.api.subscription

import net.corda.messaging.api.processor.CompactedProcessor
import java.util.*

/**
 * A subscription that can be used to manage the life cycle of consumption of event records from a topic.
 * Events are then passed to an event processor. Events returned from the processor are produced to one or more topics.
 * Consumption of records, processing and production of new records is done atomically.
 * Subscription will begin consuming events upon start().
 * Subscription will stop consuming events and close the connection upon close()/stop()
 */
interface Subscription<K, V> : LifeCycle {

    /**
     * Start a subscription.
     */
    override fun start()

    /**
     * Check the state of a subscription. true if subscription is still active. false otherwise.
     */
    val isRunning: Boolean
}

/**
 * A subscription that can be used to manage the life cycle of consumption of event records from a topic.
 * The state for a given record is retrieved and both then passed to a state + event processor.
 * State and Events returned from the processor are produced to one or more topics.
 * Consumption of records, processing and production of new records is done atomically
 * (that is, within a single _transaction_).
 */
interface StateAndEventSubscription<K : Any, S : Any> : LifeCycle {
    /**
     *  Queries the topic values for the most recent state [S] of the given [key].
     *  For partitioned topics not all values may be available.  However, any key
     *  provided by [StateAndEventProcessor.onNext] will guaranteed available.
     *
     *  @param key the topic key for a given state
     *  @return the current state for the given key, or null if it's not available
     *  @throws IllegalArgumentException when the [key] is on a remotely managed partition
     */
    @Throws(IllegalArgumentException::class)
    fun getValue(key: K): S?
}

/**
 * This subscription should be used when consuming records from a compacted topic
 * (see https://kafka.apache.org/documentation.html#compaction).  [CompactedSubscription] differs from
 * [Subscription] in that it:
 *
 *     - guarantees that every record in the topic will be provided
 *     - each record provided will be the most recent version of that record
 *
 * For details of how the data will be provided see [CompactedProcessor].
 */
interface CompactedSubscription<K : Any, V : Any> : Subscription<K, V> {
    /**
     *  Queries the topic values for the most recent value [V] of the given [key]
     */
    fun getValue(key: K): V?
}
