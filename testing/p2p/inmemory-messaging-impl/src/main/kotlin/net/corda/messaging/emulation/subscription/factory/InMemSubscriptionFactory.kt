package net.corda.messaging.emulation.subscription.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
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
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.emulation.rpc.RPCTopicService
import net.corda.messaging.emulation.subscription.compacted.InMemoryCompactedSubscription
import net.corda.messaging.emulation.subscription.durable.DurableSubscription
import net.corda.messaging.emulation.subscription.eventlog.EventLogSubscription
import net.corda.messaging.emulation.subscription.pubsub.PubSubSubscription
import net.corda.messaging.emulation.subscription.rpc.RPCSubscriptionImpl
import net.corda.messaging.emulation.subscription.stateandevent.InMemoryStateAndEventSubscription
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID

/**
 * In memory implementation of the Subscription Factory.
 */
@Component(service = [SubscriptionFactory::class])
class InMemSubscriptionFactory @Activate constructor(
    @Reference(service = TopicService::class)
    private val topicService: TopicService,
    @Reference(service = RPCTopicService::class)
    private val rpcTopicService: RPCTopicService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : SubscriptionFactory {

    override fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        messagingConfig: SmartConfig
    ): Subscription<K, V> {
        return PubSubSubscription(
            subscriptionConfig,
            processor,
            topicService,
            lifecycleCoordinatorFactory,
            UUID.randomUUID().toString()
        )
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        messagingConfig: SmartConfig,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        return DurableSubscription(
            subscriptionConfig,
            processor,
            partitionAssignmentListener,
            topicService,
            lifecycleCoordinatorFactory,
            messagingConfig.getInt(INSTANCE_ID)
        )
    }

    override fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        messagingConfig: SmartConfig
    ): CompactedSubscription<K, V> {
        return InMemoryCompactedSubscription(
            subscriptionConfig,
            processor,
            topicService,
            lifecycleCoordinatorFactory,
            UUID.randomUUID().toString()
        )
    }

    override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        messagingConfig: SmartConfig,
        stateAndEventListener: StateAndEventListener<K, S>?
    ): StateAndEventSubscription<K, S, E> {
        return InMemoryStateAndEventSubscription(
            subscriptionConfig,
            processor,
            stateAndEventListener,
            topicService,
            lifecycleCoordinatorFactory,
            messagingConfig.getInt(INSTANCE_ID)
        )
    }

    override fun <K : Any, S : Any, E : Any> createPriorityStreamSubscription(
        subscriptionConfig: SubscriptionConfig,
        topics: Map<Int, List<String>>,
        processor: StateAndEventProcessor<K, S, E>,
        messagingConfig: SmartConfig
    ): StateAndEventSubscription<K, S, E> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        messagingConfig: SmartConfig,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        return EventLogSubscription(
            subscriptionConfig,
            processor,
            partitionAssignmentListener,
            topicService,
            lifecycleCoordinatorFactory,
            messagingConfig.getInt(INSTANCE_ID)
        )
    }

    override fun <REQUEST : Any, RESPONSE : Any> createRPCSubscription(
        rpcConfig: RPCConfig<REQUEST, RESPONSE>,
        messagingConfig: SmartConfig,
        responderProcessor: RPCResponderProcessor<REQUEST, RESPONSE>
    ): RPCSubscription<REQUEST, RESPONSE> {
        return RPCSubscriptionImpl(
            rpcConfig,
            rpcTopicService,
            responderProcessor,
            lifecycleCoordinatorFactory,
            UUID.randomUUID().toString()
        )
    }

    override fun <V : Any> createRestSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<String, V>,
        messagingConfig: SmartConfig,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<String, V> {
        TODO("Not yet implemented")
    }
}
