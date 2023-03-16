package net.corda.messaging.subscription.factory

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import net.corda.data.CordaAvroSerializationFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.chunking.MessagingChunkFactory
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
import net.corda.messaging.config.MessagingConfigResolver
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.subscription.CompactedSubscriptionImpl
import net.corda.messaging.subscription.DurableSubscriptionImpl
import net.corda.messaging.subscription.EventLogSubscriptionImpl
import net.corda.messaging.subscription.PubSubSubscriptionImpl
import net.corda.messaging.subscription.RPCSubscriptionImpl
import net.corda.messaging.subscription.StateAndEventSubscriptionImpl
import net.corda.messaging.subscription.consumer.builder.StateAndEventBuilder
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

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
    @Reference(service = MessagingChunkFactory::class)
    private val messagingChunkFactory: MessagingChunkFactory,
) : SubscriptionFactory {

    // Used to ensure that each subscription has a unique client.id
    private val clientIdCounter = AtomicLong()

    override fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        messagingConfig: SmartConfig
    ): Subscription<K, V> {
        val config = getConfig(SubscriptionType.PUB_SUB, subscriptionConfig, messagingConfig)
        return PubSubSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            processor,
            lifecycleCoordinatorFactory
        )
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        messagingConfig: SmartConfig,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        if (!messagingConfig.hasPath(INSTANCE_ID)) {
            throw CordaMessageAPIFatalException(
                "Cannot create durable subscription producer for $subscriptionConfig. No instanceId configured"
            )
        }
        val config = getConfig(SubscriptionType.DURABLE, subscriptionConfig, messagingConfig)
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
        messagingConfig: SmartConfig
    ): CompactedSubscription<K, V> {
        val config = getConfig(SubscriptionType.COMPACTED, subscriptionConfig, messagingConfig)
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
        messagingConfig: SmartConfig,
        stateAndEventListener: StateAndEventListener<K, S>?
    ): StateAndEventSubscription<K, S, E> {
        val config = getConfig(SubscriptionType.STATE_AND_EVENT, subscriptionConfig, messagingConfig)
        val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> { }
        return StateAndEventSubscriptionImpl(
            config,
            stateAndEventBuilder,
            processor,
            serializer,
            lifecycleCoordinatorFactory,
            messagingChunkFactory.createChunkSerializerService(messagingConfig.getLong(MAX_ALLOWED_MSG_SIZE)),
            stateAndEventListener,
        )
    }

    override fun <K : Any, V : Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        messagingConfig: SmartConfig,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        if (!messagingConfig.hasPath(INSTANCE_ID)) {
            throw CordaMessageAPIFatalException(
                "Cannot create durable subscription producer for $subscriptionConfig. No instanceId configured"
            )
        }
        val config = getConfig(SubscriptionType.EVENT_LOG, subscriptionConfig, messagingConfig)
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
        messagingConfig: SmartConfig,
        responderProcessor: RPCResponderProcessor<REQUEST, RESPONSE>
    ): RPCSubscription<REQUEST, RESPONSE> {
        val config = getConfig(SubscriptionType.RPC_RESPONDER, rpcConfig, messagingConfig)
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
        subscriptionType: SubscriptionType,
        subscriptionConfig: SubscriptionConfig,
        messagingConfig: SmartConfig
    ): ResolvedSubscriptionConfig {
        val configBuilder = MessagingConfigResolver(messagingConfig.factory)
        return configBuilder.buildSubscriptionConfig(
            subscriptionType,
            subscriptionConfig,
            messagingConfig,
            clientIdCounter.getAndIncrement()
        )
    }

    private fun getConfig(
        subscriptionType: SubscriptionType,
        rpcConfig: RPCConfig<*, *>,
        messagingConfig: SmartConfig
    ): ResolvedSubscriptionConfig {
        val subscriptionConfig =
            SubscriptionConfig(rpcConfig.groupName, rpcConfig.requestTopic)
        return getConfig(subscriptionType, subscriptionConfig, messagingConfig)
    }
}
