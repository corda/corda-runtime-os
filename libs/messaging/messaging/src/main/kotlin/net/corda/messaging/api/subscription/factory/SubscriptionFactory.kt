package net.corda.messaging.api.subscription.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import java.util.concurrent.ExecutorService

/**
 * Factory for creating subscriptions. Factory can be obtained an OSGi Service.
 *
 */
interface SubscriptionFactory {

    /**
     * Create a [Subscription] for processing events with keys (of type [K]) and values (of type [V]) from
     * the topic. The event feed will be a non durable queue.
     *
     * The following rules apply to this subscription:
     *   - The subscription will only receive events that occur after subscription is started. Older events are ignored.
     *   - Events will be processed at most once. If errors are thrown processing records then they will be skipped.
     *   - When no executor is provided, events are consumed and synchronously committed back to the source.
     *   - When an executor is provided, events are consumed on the executor thread, though this can cause
     *   events to be published in a different order than when handled synchronously.
     *
     * More details about the feed updates can be found in the [PubSubProcessor].
     *
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param processor This provides the callback mechanism for feed updates (see [PubSubProcessor])
     * @param executor This will allow for the threading model to be controlled by the subscriber. If null processor will
     * execute on the same thread as the consumer.
     * @param messagingConfig Configuration to override the default settings for the subscription
     * @return A [Subscription] with key (of type [K]) and value (of type [V]) to manage lifecycle.
     */
    fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        messagingConfig: SmartConfig = SmartConfigImpl.empty()
    ): Subscription<K, V>

    /**
     * Create a [Subscription] for processing events with keys (of type [K]) and values (of type [V]) from a
     * durable queue.
     *
     * Records will be marked as processed atomically at the same time as publication of the new records returned
     * by the processor. Assuming the processing logic does not have any external side-effects, this will provide
     * exactly-once semantics.
     *
     * More details about the feed updates can be found in the [DurableProcessor].
     *
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param processor This provides the callback mechanism for feed updates (see [CompactedProcessor])
     * @param messagingConfig Configuration to override the default settings for the subscription
     * @param partitionAssignmentListener a listener that reacts to partition assignment and revocations.
     * @return A [Subscription] with key (of type [K]) and value (of type [V]) to manage lifecycle.
     */
    fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        messagingConfig: SmartConfig = SmartConfigImpl.empty(),
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V>

    /**
     * Create a [CompactedSubscription] for processing events with keys (of type [K]) and values (of type [V]) from
     * the most recent state of the topic.
     *
     * The subscription will provide the current, most up-to-date state (snapshot) for the topic and subsequent updates.
     * More details about the feed updates can be found in the [CompactedProcessor].
     *
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param processor This provides the callback mechanism for feed updates (see [CompactedProcessor])
     * @param messagingConfig Configuration to override the default settings for the subscription
     * @return A subscription to manage lifecycle.
     */
    fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        messagingConfig: SmartConfig = SmartConfigImpl.empty(),
    ): CompactedSubscription<K, V>

    /**
     * Create a subscription for processing events from a durable topic, with a corresponding state on a compacted topic.
     *
     * This generally mimics the Actor pattern: receive event, update state, publish new state and follow-up events
     *
     * Events (of type [E]) and states (of type [S]) will be on the different topics but will use the same key
     * (of type [K]) to ensure correct matching. The state may be null for a given event. Events will be processed
     * exactly once.
     *
     * More details about the feed updates can be found in the [StateAndEventProcessor].
     *
     * NOTE: The returned events will be published and the processed events will be consumed atomically as a
     * single transaction.
     *
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param processor This provides the callback mechanism for feed updates (see [StateAndEventProcessor])
     * @param messagingConfig Configuration to override the default settings for the subscription
     * @param stateAndEventListener listener to give client access to the in-memory map of states
     * @return A [StateAndEventSubscription] to manage lifecycle.
     */
    fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        messagingConfig: SmartConfig = SmartConfigImpl.empty(),
        stateAndEventListener: StateAndEventListener<K, S>? = null
    ): StateAndEventSubscription<K, S, E>

    /**
     * Creates an event log subscription.
     * @param processor the processor that will be wired up with the created subscription.
     * @param subscriptionConfig Define the mandatory params for creating a subscription.
     * @param messagingConfig Map of properties to override the default settings for the connection to the source of events
     * @param partitionAssignmentListener a listener that reacts to partition assignment and revocations.
     */
    fun <K : Any, V : Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        messagingConfig: SmartConfig = SmartConfigImpl.empty(),
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V>

    /**
     * Create an instance of the [RPCSubscription]
     * This subscription is used to pick up requests of type [REQUEST] posted by [RPCSender]
     * The request is then processed and a response of type [RESPONSE] is posted back to the sender
     *
     * RPC requests are handled asynchronously. Input messages are consumes as soon as they are posted to the user
     * event handler. RPC responses are unreliable so do not use this pattern if you require reliable responses for
     * your requests
     *
     * On start, the subscription goes to the latest message on the topic and not to the last one that was consumed.
     * This means that any requests posted during a period of response side unavailability will not be processed
     * (similar to the pub/sub pattern)
     *
     * @param rpcConfig Define the mandatory params for creating a subscription.
     * @param messagingConfig Map of properties to override the default settings for the connection to the source of events
     * @param responderProcessor processor in charge of handling incoming requests
     */
    fun <REQUEST : Any, RESPONSE : Any> createRPCSubscription(
        rpcConfig: RPCConfig<REQUEST, RESPONSE>,
        messagingConfig: SmartConfig = SmartConfigImpl.empty(),
        responderProcessor: RPCResponderProcessor<REQUEST, RESPONSE>
    ): RPCSubscription<REQUEST, RESPONSE>
}
