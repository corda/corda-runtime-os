package impl.samples.subscription.factory.impl


import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import impl.samples.subscription.subscriptions.StateAndEventSubscriptionImpl
import impl.samples.subscription.subscriptions.DurableQueueSubscriptionImpl
import impl.samples.subscription.subscriptions.PubSubSubscriptionImpl
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import java.util.concurrent.ExecutorService

class SubscriptionFactoryImpl : SubscriptionFactory {

    override fun <K, S, E> createStateAndEventSubscription(config: StateAndEventSubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        properties: Map<String, String>
    ): StateAndEventSubscription<K, S, E> {
        return StateAndEventSubscriptionImpl(config.groupName, config.instanceId, config.eventTopic,
            config.stateTopic, processor, properties)
    }

    override fun <K, V> createPubSubSubscription(
        config: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService,
        properties: Map<String, String>
    ): Subscription<K, V> {
        return PubSubSubscriptionImpl(config.groupName, config.instanceId, config.eventTopic,
            processor, executor, properties)
    }

    override fun <K, V> createDurableSubscription(
        config: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        properties: Map<String, String>
    ): Subscription<K, V> {
        return DurableQueueSubscriptionImpl(config.groupName, config.instanceId, config.eventTopic,
            processor, properties)
    }


}