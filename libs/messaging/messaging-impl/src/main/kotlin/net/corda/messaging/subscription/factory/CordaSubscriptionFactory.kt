package net.corda.messaging.subscription.factory

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.CordaAvroSerializationFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.consumer.builder.StateAndEventBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.StateAndEventConfig.Companion.getStateAndEventConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.GROUP
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_COMPACTED
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_DURABLE
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_EVENTLOG
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_PUBSUB
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_RPC_RESPONDER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_STATEANDEVENT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC
import net.corda.messaging.kafka.utils.ConfigUtils.Companion.resolveSubscriptionConfiguration
import net.corda.messaging.kafka.utils.toConfig
import net.corda.messaging.subscription.CordaCompactedSubscriptionImpl
import net.corda.messaging.subscription.CordaDurableSubscriptionImpl
import net.corda.messaging.subscription.CordaEventLogSubscriptionImpl
import net.corda.messaging.subscription.CordaPubSubSubscriptionImpl
import net.corda.messaging.subscription.CordaRPCSubscriptionImpl
import net.corda.messaging.subscription.CordaStateAndEventSubscriptionImpl
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
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
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

        val config = resolveSubscriptionConfiguration(
            subscriptionConfig.toConfig(),
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_PUBSUB
        )

        return CordaPubSubSubscriptionImpl(
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

        val config = resolveSubscriptionConfiguration(
            subscriptionConfig.toConfig(),
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_DURABLE
        )
        return CordaDurableSubscriptionImpl(
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
        val config = resolveSubscriptionConfiguration(
            subscriptionConfig.toConfig(),
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_COMPACTED
        )
        val mapFactory = object : MapFactory<K, V> {
            override fun createMap(): MutableMap<K, V> = ConcurrentHashMap<K, V>()
            override fun destroyMap(map: MutableMap<K, V>) {
                map.clear()
            }
        }

        return CordaCompactedSubscriptionImpl(
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

        val subscriptionConfiguration = subscriptionConfig.toConfig()
        val config = resolveSubscriptionConfiguration(
            subscriptionConfiguration,
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_STATEANDEVENT
        )

        val stateAndEventConfig = getStateAndEventConfig(config)

        val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> { }
        return CordaStateAndEventSubscriptionImpl(
            stateAndEventConfig,
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

        val config = resolveSubscriptionConfiguration(
            subscriptionConfig.toConfig(),
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_EVENTLOG
        )

        return CordaEventLogSubscriptionImpl(
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

        val rpcConfiguration = ConfigFactory.empty()
            .withValue(GROUP, ConfigValueFactory.fromAnyRef(rpcConfig.groupName))
            .withValue(TOPIC, ConfigValueFactory.fromAnyRef(rpcConfig.requestTopic))
            .withValue("clientName", ConfigValueFactory.fromAnyRef(rpcConfig.clientName))

        val config = resolveSubscriptionConfiguration(
            rpcConfiguration,
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_RPC_RESPONDER
        )

        val cordaAvroSerializer = cordaAvroSerializationFactory.createAvroSerializer<RESPONSE>{ }
        val cordaAvroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({ }, rpcConfig.requestType)
        val publisher = publisherFactory.createPublisher(PublisherConfig(rpcConfig.clientName), nodeConfig)

        return CordaRPCSubscriptionImpl(
            config,
            publisher,
            cordaConsumerBuilder,
            responderProcessor,
            cordaAvroSerializer,
            cordaAvroDeserializer,
            lifecycleCoordinatorFactory
        )
    }
}
