package net.corda.messaging.impl.subscription.factory

import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.impl.subscription.subscriptions.pubsub.PubSubSubscription
import net.corda.messaging.impl.subscription.subscriptions.pubsub.service.TopicService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ExecutorService

/**
 * In memory implementation of the Subscription Factory.
 */
@Component
class InMemSubscriptionFactory @Activate constructor(
    @Reference(service = TopicService::class)
    private val topicService: TopicService
) : SubscriptionFactory {

    override fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        properties: Map<String, String>
    ): Subscription<K, V> {
        val defaultKafkaConfig = ConfigFactory.load("tmpInMemDefaults")
        return PubSubSubscription(subscriptionConfig, defaultKafkaConfig, processor, executor, topicService)
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        properties: Map<String, String>
    ): Subscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: StateAndEventSubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        properties: Map<String, String>
    ): StateAndEventSubscription<K, S, E> {
        TODO("Not yet implemented")
    }
}
