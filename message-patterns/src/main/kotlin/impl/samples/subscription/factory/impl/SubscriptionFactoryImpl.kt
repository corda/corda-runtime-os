package impl.samples.subscription.factory.impl


import api.samples.processor.ActorProcessor
import api.samples.processor.DurableProcessor
import api.samples.processor.PubSubProcessor
import api.samples.subscription.LifeCycle
import api.samples.subscription.factory.SubscriptionFactory
import impl.samples.subscription.subscriptions.ActorSubscription
import impl.samples.subscription.subscriptions.DurableQueueSubscription
import impl.samples.subscription.subscriptions.PubSubSubscription
import java.util.concurrent.ExecutorService

class SubscriptionFactoryImpl : SubscriptionFactory {

    override fun <K, S, E> createActorSubscription(
        groupName: String,
        instanceId: Int,
        eventTopic: String,
        stateTopic: String,
        processor: ActorProcessor<K, S, E>,
        properties: Map<String, String>
    ): LifeCycle {
        return ActorSubscription(groupName, instanceId, eventTopic, stateTopic, processor, properties)
    }

    override fun <K, V> createPubSubSubscription(
        groupName: String,
        instanceId: Int,
        eventTopic: String,
        pubsubProcessor: PubSubProcessor<K, V>,
        executor: ExecutorService,
        properties: Map<String, String>
    ): LifeCycle {
        return PubSubSubscription(groupName, instanceId, eventTopic, pubsubProcessor, executor, properties)
    }

    override fun <K, V> createDurableSubscription(
        groupName: String,
        instanceId: Int,
        eventTopic: String,
        durableProcessor: DurableProcessor<K, V>,
        properties: Map<String, String>
    ): LifeCycle {
        return DurableQueueSubscription(groupName, instanceId, eventTopic, durableProcessor, properties)
    }


}