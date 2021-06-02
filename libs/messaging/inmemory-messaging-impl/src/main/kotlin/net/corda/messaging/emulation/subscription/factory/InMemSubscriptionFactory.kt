package net.corda.messaging.emulation.subscription.factory

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.JournalSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.subscription.pubsub.PubSubSubscription
import net.corda.messaging.emulation.topic.service.TopicService
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

    companion object {
        const val EVENT_TOPIC = "topic"
        const val GROUP_NAME = "groupName"
    }

    override fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        properties: Map<String, String>
    ): Subscription<K, V> {
        //TODO - replace with config service
        val config = ConfigFactory.load("tmpInMemDefaults")
            .withValue(GROUP_NAME, ConfigValueFactory.fromAnyRef(subscriptionConfig.groupName))
            .withValue(EVENT_TOPIC, ConfigValueFactory.fromAnyRef(subscriptionConfig.eventTopic))
        return PubSubSubscription(config, processor, executor, topicService)
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        properties: Map<String, String>
    ): Subscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        properties: Map<String, String>
    ): CompactedSubscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: StateAndEventSubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        properties: Map<String, String>
    ): StateAndEventSubscription<K, S, E> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        properties: Map<String, String>
    ): Subscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> createJournalSubscription(
        subscriptionConfig: SubscriptionConfig,
        properties: Map<String, String>
    ): JournalSubscription<K, V> {
        TODO("Not yet implemented")
    }
}
