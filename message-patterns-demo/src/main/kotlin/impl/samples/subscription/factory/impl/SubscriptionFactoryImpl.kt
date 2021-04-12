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
import java.util.concurrent.ExecutorService

class SubscriptionFactoryImpl : SubscriptionFactory {

    override fun <K, S, E> createActorSubscription(
        groupName: String,
        instanceId: Int,
        eventTopic: String,
        stateTopic: String,
        processor: StateAndEventProcessor<K, S, E>,
        properties: Map<String, String>
    ): StateAndEventSubscription<K, S, E> {
        return StateAndEventSubscriptionImpl(groupName, instanceId, eventTopic, stateTopic, processor, properties)
    }

    override fun <K, V> createPubSubSubscription(
        groupName: String,
        instanceId: Int,
        eventTopic: String,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService,
        properties: Map<String, String>
    ): Subscription<K, V> {
        return PubSubSubscriptionImpl(groupName, instanceId, eventTopic, processor, executor, properties)
    }

    override fun <K, V> createDurableSubscription(
        groupName: String,
        instanceId: Int,
        eventTopic: String,
        processor: DurableProcessor<K, V>,
        properties: Map<String, String>
    ): Subscription<K, V> {
        return DurableQueueSubscriptionImpl(groupName, instanceId, eventTopic, processor, properties)
    }


}