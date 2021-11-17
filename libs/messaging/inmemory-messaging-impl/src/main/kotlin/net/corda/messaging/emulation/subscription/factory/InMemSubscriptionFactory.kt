package net.corda.messaging.emulation.subscription.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.RandomAccessSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.emulation.rpc.RPCTopicService
import net.corda.messaging.emulation.subscription.compacted.InMemoryCompactedSubscription
import net.corda.messaging.emulation.subscription.durable.DurableSubscription
import net.corda.messaging.emulation.subscription.eventlog.EventLogSubscription
import net.corda.messaging.emulation.subscription.pubsub.PubSubSubscription
import net.corda.messaging.emulation.subscription.rpc.RPCSubscriptionImpl
import net.corda.messaging.emulation.subscription.stateandevent.InMemoryStateAndEventSubscription
import net.corda.messaging.emulation.topic.service.TopicService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ExecutorService

/**
 * In memory implementation of the Subscription Factory.
 */
@Component(service = [SubscriptionFactory::class])
class InMemSubscriptionFactory @Activate constructor(
    @Reference(service = TopicService::class)
    private val topicService: TopicService,
    @Reference(service = RPCTopicService::class)
    private val rpcTopicService: RPCTopicService
) : SubscriptionFactory {

    override fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        nodeConfig: SmartConfig
    ): Subscription<K, V> {
        return PubSubSubscription(subscriptionConfig, processor, executor, topicService)
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        nodeConfig: SmartConfig,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        return DurableSubscription(
            subscriptionConfig,
            processor,
            partitionAssignmentListener,
            topicService
        )
    }

    override fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        nodeConfig: SmartConfig
    ): CompactedSubscription<K, V> {
        return InMemoryCompactedSubscription(
            subscriptionConfig,
            processor,
            topicService
        )
    }

    override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        nodeConfig: SmartConfig,
        stateAndEventListener: StateAndEventListener<K, S>?,
    ): StateAndEventSubscription<K, S, E> {
        return InMemoryStateAndEventSubscription(
            subscriptionConfig,
            processor,
            stateAndEventListener,
            topicService,
        )
    }

    override fun <K : Any, V : Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        nodeConfig: SmartConfig,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        return EventLogSubscription(
            subscriptionConfig,
            processor,
            partitionAssignmentListener,
            topicService,
        )
    }

    override fun <K : Any, V : Any> createRandomAccessSubscription(
        subscriptionConfig: SubscriptionConfig,
        nodeConfig: SmartConfig,
        keyClass: Class<K>,
        valueClass: Class<V>
    ): RandomAccessSubscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <REQUEST : Any, RESPONSE : Any> createRPCSubscription(
        rpcConfig: RPCConfig<REQUEST, RESPONSE>,
        nodeConfig: SmartConfig,
        responderProcessor: RPCResponderProcessor<REQUEST, RESPONSE>
    ): RPCSubscription<REQUEST, RESPONSE> {
        return RPCSubscriptionImpl(rpcConfig.requestTopic,rpcTopicService,responderProcessor)
    }
}
