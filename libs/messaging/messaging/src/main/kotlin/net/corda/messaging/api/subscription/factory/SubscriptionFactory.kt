package net.corda.messaging.api.subscription.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.RandomAccessSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import java.util.concurrent.ExecutorService

/**
 * Factory for creating subscriptions. Factory can be obtained an OSGi Service.
 */
interface SubscriptionFactory {

    /**
     * Create a PubSub subscription for processing events from a non durable queue.
     * Subscription will only receive events that occur after subscription is started. Older events are ignored.
     * Events will be processed at most once. If errors are thrown processing records then they will be skipped.
     * Events are consumed and synchronously committed back to the source.
     * @param processor Processor of events. Processor does not return any new records to be sent back to the topic.
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param nodeConfig Map of properties to override the default settings for the connection to the source of events
     * @param executor This will allow for the threading model to be controlled by the subscriber. If null processor will
     * execute on the same thread as the consumer.
     * @return A subscription to manage lifecycle.
     */
    fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        nodeConfig: Config = ConfigFactory.empty()
    ): Subscription<K, V>

    /**
     * Create a subscription for processing events from a durable queue.
     * Events will be processed exactly once. Consumer will marked records as consumed after they have been processed and
     * any new records have been committed back to the topic.
     * @param processor will pick up all events published to the event source that have not been previously processed. Each record will
     * be given the processor once.
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param nodeConfig Map of properties to override the default settings for the connection to the source of events
     * @param partitionAssignmentListener a listener that reacts to partition assignment and revocations.
     * @return A subscription to manage lifecycle.
     */
   fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        nodeConfig: Config = ConfigFactory.empty(),
        partitionAssignmentListener: PartitionAssignmentListener?
    ) : Subscription<K, V>

    /**
     * Create a subscription for processing events from the most recent state of the topic.
     * The subscription will get the current, most up-to-date state (snapshot) for the topic and subsequent updates.
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param nodeConfig Map of properties to override the default settings for the connection to the source of events
     * @return A subscription to manage lifecycle.
     */
    fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        nodeConfig: Config = ConfigFactory.empty(),
    ) : CompactedSubscription<K, V>

    /**
     * Create a subscription for processing events which also have a state from a durable queue.
     * The subscription will get the correct state for an event. Events and States will be retrieved
     * from different topics using the same key. State may be null for a given event.
     * Events will be processed exactly once. Consumer will marked records as consumed after they have been processed and
     * any new records have been committed back to the topic.
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param properties Map of properties to override the default settings for the connection to the source of events
     * @return A subscription to manage lifecycle.
     */
    fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        nodeConfig: Config = ConfigFactory.empty()
    ) : StateAndEventSubscription<K, S, E>

    /**
     * Creates an event log subscription.
     * @param processor the processor that will be wired up with the created subscription.
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param nodeConfig Map of properties to override the default settings for the connection to the source of events
     * @param partitionAssignmentListener a listener that reacts to partition assignment and revocations.
     */
    fun <K: Any, V: Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        nodeConfig: Config = ConfigFactory.empty(),
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V>

    /**
     * Creates a random access subscription.
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param nodeConfig Map of properties to override the default settings for the connection to the source of events
     */
    fun <K: Any, V: Any> createRandomAccessSubscription(
        subscriptionConfig: SubscriptionConfig,
        nodeConfig: Config = ConfigFactory.empty(),
    ): RandomAccessSubscription<K, V>
    
}
