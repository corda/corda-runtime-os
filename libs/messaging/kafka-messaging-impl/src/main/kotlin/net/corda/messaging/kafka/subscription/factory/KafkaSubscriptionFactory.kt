package net.corda.messaging.kafka.subscription.factory

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.kafka.subscription.subscriptions.pubsub.KafkaPubSubSubscription
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.impl.PubSubConsumerBuilder
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ExecutorService

/**
 * Kafka implementation of the Subscription Factory.
 */
@Component
class KafkaSubscriptionFactory : SubscriptionFactory {

    override fun <K, V> createPubSubSubscription(
        config: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        properties: Map<String, String>
    ): Subscription<K, V> {
        return KafkaPubSubSubscription(config, properties, PubSubConsumerBuilder(), processor, executor)
    }

    override fun <K, V> createDurableSubscription(
        config: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        properties: Map<String, String>
    ): Subscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K, S, E> createStateAndEventSubscription(
        config: StateAndEventSubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        properties: Map<String, String>
    ): StateAndEventSubscription<K, S, E> {
        TODO("Not yet implemented")
    }
}