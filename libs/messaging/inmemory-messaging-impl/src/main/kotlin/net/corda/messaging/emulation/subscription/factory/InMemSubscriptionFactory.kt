package net.corda.messaging.emulation.subscription.factory

import com.typesafe.config.Config
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
import net.corda.messaging.api.subscription.listener.LifecycleListener
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.emulation.subscription.compacted.InMemoryCompactedSubscription
import net.corda.messaging.emulation.subscription.durable.DurableSubscription
import net.corda.messaging.emulation.subscription.eventlog.EventLogSubscription
import net.corda.messaging.emulation.subscription.pubsub.PubSubSubscription
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
    private val topicService: TopicService
) : SubscriptionFactory {

    override fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        nodeConfig: Config,
        lifecycleListener: LifecycleListener?
    ): Subscription<K, V> {
        return PubSubSubscription(subscriptionConfig, processor, executor, topicService, lifecycleListener)
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        nodeConfig: Config,
        partitionAssignmentListener: PartitionAssignmentListener?,
        lifecycleListener: LifecycleListener?
    ): Subscription<K, V> {
        return DurableSubscription(
            subscriptionConfig,
            processor,
            partitionAssignmentListener,
            topicService,
            lifecycleListener
        )
    }

    override fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        nodeConfig: Config,
        lifecycleListener: LifecycleListener?
    ): CompactedSubscription<K, V> {
        return InMemoryCompactedSubscription(
            subscriptionConfig,
            processor,
            topicService,
            lifecycleListener
        )
    }

    override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        nodeConfig: Config,
        stateAndEventListener: StateAndEventListener<K, S>?,
        lifecycleListener: LifecycleListener?,
    ): StateAndEventSubscription<K, S, E> {
        return InMemoryStateAndEventSubscription(
            subscriptionConfig,
            processor,
            stateAndEventListener,
            topicService,
            lifecycleListener
        )
    }

    override fun <K : Any, V : Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        nodeConfig: Config,
        partitionAssignmentListener: PartitionAssignmentListener?,
        lifecycleListener: LifecycleListener?
    ): Subscription<K, V> {
        return EventLogSubscription(
            subscriptionConfig,
            processor,
            partitionAssignmentListener,
            topicService,
            lifecycleListener
        )
    }

    override fun <K : Any, V : Any> createRandomAccessSubscription(
        subscriptionConfig: SubscriptionConfig,
        nodeConfig: Config,
        keyClass: Class<K>,
        valueClass: Class<V>,
        lifecycleListener: LifecycleListener?
    ): RandomAccessSubscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <TREQ : Any, TRESP : Any> createRPCSubscription(
        rpcConfig: RPCConfig<TREQ, TRESP>,
        nodeConfig: Config,
        responderProcessor: RPCResponderProcessor<TREQ, TRESP>,
        lifecycleListener: LifecycleListener?
    ): RPCSubscription<TREQ, TRESP> {
        TODO("Not yet implemented")
    }
}
