package net.corda.messaging.api.subscription

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

/**
 * A subscription that can be used to manage the life cycle of consumption of event records from a topic.
 * Records are key/value pairs represented by [K] and [V], respectively, and are analogous to a kafka record.
 *
 * See [SubscriptionFactory] for the creation of each subscription.
 *
 * Each subscription will have a different processor for sending feed updates to the user.  See
 * [SubscriptionFactory] and the processor docs themselves for more details on each type.
 *
 * A subscription will begin consuming events upon start().
 * A subscription will stop consuming events and close the connection upon close()/stop()
 */
interface Subscription<K, V> : Lifecycle {

    /**
     * Start a subscription.
     */
    override fun start()

    /**
     * Check the state of a subscription. true if subscription is still active. false otherwise.
     */
    override val isRunning: Boolean

    /**
     * The name of the lifecycle coordinator inside the subscription. You can register a different coordinator to listen
     * for status changes from this subscription by calling [followStatusChangesByName] and passing in this value.
     */
    val subscriptionName: LifecycleCoordinatorName
}

/**
 * A subscription that handles requests of type [REQUEST], processes the request and publishes the response in type [RESPONSE]
 *
 * RPC requests are processed asynchronously. Input messages are consume as soon as they have been posted to the
 * user event handler. RPC responses are unreliable so do not use this pattern if reliable response are required.
 *
 * See [SubscriptionFactory] for the creation of each subscription.
 *
 * Each subscription will have a different processor for sending feed updates to the user.  See
 * [SubscriptionFactory] and the processor docs themselves for more details on each type.
 *
 * A subscription will begin consuming events upon start().
 *
 * On first connection, the subscription goes to the latest message on the topic and not the last one consumed.
 * This means that any requests sent when the response side is not yet operational will not be processed
 * (similar to pub/sub pattern)
 *
 * A subscription will stop consuming events and close the connection upon close()/stop()
 */
interface RPCSubscription<REQUEST, RESPONSE> : Lifecycle {

    /**
     * The name of the lifecycle coordinator inside the subscription. You can register a different coordinator to listen
     * for status changes from this subscription by calling [followStatusChangesByName] and passing in this value.
     */
    val subscriptionName: LifecycleCoordinatorName

}

/**
 * A subscription that can be used to manage the life cycle of consumption of both state and event records from a
 * pair of topics.
 *
 * [StateAndEventSubscription]s actually process two feeds, one for states and one for events.  The state feed is
 * treated as, and probably is, a compacted topic.  The subscription will retain the most recent state from the feed.
 * The events are treated as a durable feed as to avoid missing any.  Each event may then trigger an update of the
 * corresponding state or, indeed, trigger more events.
 *
 * See [SubscriptionFactory] for the creation of this subscription.
 *
 * Feed updates will be returned via a [StateAndEventProcessor].
 *
 * Consumption of records, processing and production of new records on a given key [K] is done atomically
 * (that is, within a single _transaction_).  However, records for different keys may be batched up to
 * improve performance.
 */
interface StateAndEventSubscription<K, S, E> : Lifecycle {
    /**
     * The name of the lifecycle coordinator inside the subscription. You can register a different coordinator to listen
     * for status changes from this subscription by calling [followStatusChangesByName] and passing in this value.
     */
    val subscriptionName: LifecycleCoordinatorName
}

/**
 * This subscription should be used when consuming records from a compacted topic
 * (see https://kafka.apache.org/documentation.html#compaction).  [CompactedSubscription] differs from
 * [Subscription] in that it:
 *
 *     - guarantees that a record for every valid key in the topic will be provided
 *     - each record provided will be the most recent version of that record
 *
 * The subscription will initially provide the current, most up-to-date state (snapshot) for the topic; then
 * will provide subsequent updates.
 *
 * For more details on how the feed updates will be provided see [CompactedProcessor].
 */
interface CompactedSubscription<K : Any, V : Any> : Subscription<K, V> {
    /**
     *  Queries the topic values for the most recent value [V] of the given [key].
     *
     *  This is not thread-safe! It will be safer to call this from within the [CompactedProcessor] provided
     *  to the subscription in order to ensure thread safety.
     *
     *  @param key the topic key for a given state
     *  @return the current value for the given key, or null if it's not available
     */
    fun getValue(key: K): V?
}

/**
 * An interface that can be implemented for a subscription to react to assignment and revocation
 * of topic partitions.  This will be useful for handling rebalancing situations on the Kafka consumers and
 * will allow the subscription to ensure consistency after a rebalance event.
 */
interface PartitionAssignmentListener {
    /**
     * Implement this method to handle the removal of topic partitions
     *
     * @param topicPartitions the topic partitions that were unassigned.
     */
    fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>)

    /**
     * Implement this method to handle the addition of topic partitions
     *
     * @param topicPartitions the topic partitions that were assigned.
     */
    fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>)
}
