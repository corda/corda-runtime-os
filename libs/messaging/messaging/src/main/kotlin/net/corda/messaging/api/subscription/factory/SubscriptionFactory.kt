package net.corda.messaging.api.subscription.factory

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import java.util.concurrent.ExecutorService

/**
 * Factories for creating subscriptions.
 */
interface SubscriptionFactory {

    /**
     * Create a PubSub subsciption for processing events from a non durable queue.
     * [processor] will only receive events that occur after subscription is started.
     * Pass in a [config] and [properties] to define the source of events.
     * Allows an [executor] to be passed in to define what thread(s) events are processed on.
     * @return a subscription to manage lifecycle
     */
    fun <K,V> createPubSubSubscription(config: SubscriptionConfig,
                                       processor: PubSubProcessor<K, V>,
                                       executor: ExecutorService,
                                       properties: Map<String, String>): Subscription<K, V>

    /**
     * Create a subscription for processing events from a durable queue.
     * [processor] will pick up all events published to the event source that have not been previously processed.
     * Pass in a [config] and [properties] to define the source of events.
     * @return a subscription to manage lifecycle
     */
   fun <K, V> createDurableSubscription(config: SubscriptionConfig,
                                        processor: DurableProcessor<K, V>,
                                        properties: Map<String, String>) : Subscription<K, V>

    /**
     * Create a subscription for processing events which also have a state from a durable queue.
     * [processor] will pick up all events published to the event source that have not been previously processed.
     * The subscription will get the correct state for an event. Events and States will be retrieved
     * from different topics using the same key.
     * Pass in a [config] and [properties] to define the source of state and events.
     * @return a subscription to manage lifecycle
     */
   fun <K, S, E> createStateAndEventSubscription(config: StateAndEventSubscriptionConfig,
                                                 processor: StateAndEventProcessor<K, S, E>,
                                                 properties: Map<String, String>) : StateAndEventSubscription<K, S, E>
}