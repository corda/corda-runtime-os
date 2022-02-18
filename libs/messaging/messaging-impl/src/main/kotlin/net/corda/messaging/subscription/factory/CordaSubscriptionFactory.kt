package net.corda.messaging.subscription.factory

import net.corda.data.CordaAvroSerializationFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
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
import net.corda.messaging.config.ConfigResolver
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.CompactedSubscriptionImpl
import net.corda.messaging.subscription.DurableSubscriptionImpl
import net.corda.messaging.subscription.EventLogSubscriptionImpl
import net.corda.messaging.subscription.PubSubSubscriptionImpl
import net.corda.messaging.subscription.RPCSubscriptionImpl
import net.corda.messaging.subscription.StateAndEventSubscriptionImpl
import net.corda.messaging.subscription.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.subscription.consumer.builder.StateAndEventBuilder
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kafka implementation of the Subscription Factory.
 * @property cordaAvroSerializationFactory OSGi DS Injected avro schema registry
 */
@Suppress("LongParameterList")
@Component(service = [SubscriptionFactory::class])
class CordaSubscriptionFactory @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CordaProducerBuilder::class)
    private val cordaProducerBuilder: CordaProducerBuilder,
    @Reference(service = CordaConsumerBuilder::class)
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    @Reference(service = StateAndEventBuilder::class)
    private val stateAndEventBuilder: StateAndEventBuilder,
) : SubscriptionFactory {

    // Used to ensure that each subscription has a unique client.id
    private val clientIdCounter = AtomicInteger()

    override fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        nodeConfig: SmartConfig
    ): Subscription<K, V> {
//        val config = resolveSubscriptionConfiguration(
//            subscriptionConfig.toConfig(),
//            nodeConfig,
//            clientIdCounter.getAndIncrement(),
//            PATTERN_PUBSUB
//        )

        val config = getConfig(subscriptionConfig, nodeConfig)
        return PubSubSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            processor,
            executor,
            lifecycleCoordinatorFactory
        )
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        nodeConfig: SmartConfig,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        if (subscriptionConfig.instanceId == null) {
            throw CordaMessageAPIFatalException(
                "Cannot create durable subscription producer for $subscriptionConfig. No instanceId configured"
            )
        }

//        val config = resolveSubscriptionConfiguration(
//            subscriptionConfig.toConfig(),
//            nodeConfig,
//            clientIdCounter.getAndIncrement(),
//            PATTERN_DURABLE
//        )
        val config = getConfig(subscriptionConfig, nodeConfig)
        return DurableSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            partitionAssignmentListener,
            lifecycleCoordinatorFactory
        )
    }

    override fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        nodeConfig: SmartConfig
    ): CompactedSubscription<K, V> {
//        val config = resolveSubscriptionConfiguration(
//            subscriptionConfig.toConfig(),
//            nodeConfig,
//            clientIdCounter.getAndIncrement(),
//            PATTERN_COMPACTED
//        )
        val config = getConfig(subscriptionConfig, nodeConfig)
        val mapFactory = object : MapFactory<K, V> {
            override fun createMap(): MutableMap<K, V> = ConcurrentHashMap<K, V>()
            override fun destroyMap(map: MutableMap<K, V>) {
                map.clear()
            }
        }

        return CompactedSubscriptionImpl(
            config,
            mapFactory,
            cordaConsumerBuilder,
            processor,
            lifecycleCoordinatorFactory
        )
    }

    override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        nodeConfig: SmartConfig,
        stateAndEventListener: StateAndEventListener<K, S>?
    ): StateAndEventSubscription<K, S, E> {

//        val subscriptionConfiguration = subscriptionConfig.toConfig()
//        val config = resolveSubscriptionConfiguration(
//            subscriptionConfiguration,
//            nodeConfig,
//            clientIdCounter.getAndIncrement(),
//            PATTERN_STATEANDEVENT
//        )
//
//        val stateAndEventConfig = getStateAndEventConfig(config)

        val config = getConfig(subscriptionConfig, nodeConfig)
        val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> { }
        return StateAndEventSubscriptionImpl(
            config,
            stateAndEventBuilder,
            processor,
            serializer,
            lifecycleCoordinatorFactory,
            stateAndEventListener,
        )
    }

    override fun <K : Any, V : Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        nodeConfig: SmartConfig,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        if (subscriptionConfig.instanceId == null) {
            throw CordaMessageAPIFatalException(
                "Cannot create durable subscription producer for $subscriptionConfig. No instanceId configured"
            )
        }

//        val config = resolveSubscriptionConfiguration(
//            subscriptionConfig.toConfig(),
//            nodeConfig,
//            clientIdCounter.getAndIncrement(),
//            PATTERN_EVENTLOG
//        )

        val config = getConfig(subscriptionConfig, nodeConfig)
        return EventLogSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            partitionAssignmentListener,
            lifecycleCoordinatorFactory
        )
    }

    override fun <REQUEST : Any, RESPONSE : Any> createRPCSubscription(
        rpcConfig: RPCConfig<REQUEST, RESPONSE>,
        nodeConfig: SmartConfig,
        responderProcessor: RPCResponderProcessor<REQUEST, RESPONSE>
    ): RPCSubscription<REQUEST, RESPONSE> {

//        val rpcConfiguration = ConfigFactory.empty()
//            .withValue(GROUP, ConfigValueFactory.fromAnyRef(rpcConfig.groupName))
//            .withValue(TOPIC, ConfigValueFactory.fromAnyRef(rpcConfig.requestTopic))
//            .withValue("clientName", ConfigValueFactory.fromAnyRef(rpcConfig.clientName))
//
//        val config = resolveSubscriptionConfiguration(
//            rpcConfiguration,
//            nodeConfig,
//            clientIdCounter.getAndIncrement(),
//            PATTERN_RPC_RESPONDER
//        ).withoutPath(PRODUCER_TRANSACTIONAL_ID)

        val config = getConfig(rpcConfig, nodeConfig)
        val cordaAvroSerializer = cordaAvroSerializationFactory.createAvroSerializer<RESPONSE> { }
        val cordaAvroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({ }, rpcConfig.requestType)

        return RPCSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            responderProcessor,
            cordaAvroSerializer,
            cordaAvroDeserializer,
            lifecycleCoordinatorFactory
        )
    }

    private fun getConfig(
        subscriptionConfig: SubscriptionConfig,
        messagingConfig: SmartConfig
    ): ResolvedSubscriptionConfig {
        val configBuilder = ConfigResolver(messagingConfig.factory)
        val clientId = clientIdCounter.getAndIncrement()
        return configBuilder.buildSubscriptionConfig(subscriptionConfig, messagingConfig, clientId)
    }

    private fun getConfig(
        rpcConfig: RPCConfig<*, *>,
        messagingConfig: SmartConfig
    ): ResolvedSubscriptionConfig {
        val subscriptionConfig =
            SubscriptionConfig(rpcConfig.groupName, rpcConfig.requestTopic)
        return getConfig(subscriptionConfig, messagingConfig)
    }
}
