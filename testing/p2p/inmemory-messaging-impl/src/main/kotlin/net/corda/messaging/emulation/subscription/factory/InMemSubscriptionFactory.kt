package net.corda.messaging.emulation.subscription.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.*
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
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.emulation.subscription.eventsource.EventSourceSubscription
import java.util.concurrent.atomic.AtomicInteger

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
    private companion object {
        val instanceIndex = AtomicInteger()
    }

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
            instanceIndex.incrementAndGet().toString(),
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
            instanceIndex.incrementAndGet(),
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
            instanceIndex.incrementAndGet().toString()
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
            instanceIndex.incrementAndGet()
        )
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
            instanceIndex.incrementAndGet()
        )
    }

    override fun <K : Any, V : Any> createEventSourceSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventSourceProcessor<K, V>,
        messagingConfig: SmartConfig,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        return EventSourceSubscription(
            subscriptionConfig,
            processor,
            partitionAssignmentListener,
            topicService,
            lifecycleCoordinatorFactory,
            instanceIndex.incrementAndGet()
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
            instanceIndex.incrementAndGet().toString(),
        )
    }

    override fun <REQUEST : Any, RESPONSE : Any> createHttpRPCSubscription(
        rpcConfig: SyncRPCConfig,
        processor: SyncRPCProcessor<REQUEST, RESPONSE>
    ): RPCSubscription<REQUEST, RESPONSE> = throw NotImplementedError()
}
