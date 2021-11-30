package net.corda.messaging.kafka.subscription.factory

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.messaging.RPCRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
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
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.RandomAccessSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.producer.builder.impl.KafkaProducerBuilderImpl
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.GROUP
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_COMPACTED
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_DURABLE
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_EVENTLOG
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_PUBSUB
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_RANDOMACCESS
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_RPC_RESPONDER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_STATEANDEVENT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.subscription.CordaAvroDeserializer
import net.corda.messaging.kafka.subscription.KafkaCompactedSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaDurableSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaEventLogSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaPubSubSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaRPCSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaRandomAccessSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaStateAndEventSubscriptionImpl
import net.corda.messaging.kafka.subscription.consumer.builder.impl.CordaKafkaConsumerBuilderImpl
import net.corda.messaging.kafka.subscription.consumer.builder.impl.StateAndEventBuilderImpl
import net.corda.messaging.kafka.types.StateAndEventConfig.Companion.getStateAndEventConfig
import net.corda.messaging.kafka.utils.ConfigUtils.Companion.resolveSubscriptionConfiguration
import net.corda.messaging.kafka.utils.toConfig
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kafka implementation of the Subscription Factory.
 * @property avroSchemaRegistry OSGi DS Injected avro schema registry
 */
@Component(service = [SubscriptionFactory::class])
class KafkaSubscriptionFactory @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
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
        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(avroSchemaRegistry)

        return KafkaPubSubSubscriptionImpl(
            config,
            consumerBuilder,
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
        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(avroSchemaRegistry)
        val producerBuilder = KafkaProducerBuilderImpl(avroSchemaRegistry)

        return KafkaDurableSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
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
        val mapFactory = object : SubscriptionMapFactory<K, V> {
            override fun createMap(): MutableMap<K, V> = ConcurrentHashMap<K, V>()
            override fun destroyMap(map: MutableMap<K, V>) {
                map.clear()
            }
        }
        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(avroSchemaRegistry)

        return KafkaCompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
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

        val producerBuilder = KafkaProducerBuilderImpl(avroSchemaRegistry)
        val eventConsumerBuilder = CordaKafkaConsumerBuilderImpl<K, E>(avroSchemaRegistry)
        val stateConsumerBuilder = CordaKafkaConsumerBuilderImpl<K, S>(avroSchemaRegistry)

        val mapFactory = object : SubscriptionMapFactory<K, Pair<Long, S>> {
            override fun createMap(): MutableMap<K, Pair<Long, S>> = ConcurrentHashMap()
            override fun destroyMap(map: MutableMap<K, Pair<Long, S>>) = map.clear()
        }

        val stateAndEventBuilder = StateAndEventBuilderImpl(
            stateConsumerBuilder,
            eventConsumerBuilder,
            producerBuilder,
            mapFactory
        )

        return KafkaStateAndEventSubscriptionImpl(
            stateAndEventConfig,
            stateAndEventBuilder,
            processor,
            avroSchemaRegistry,
            lifecycleCoordinatorFactory,
            stateAndEventListener
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
        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(avroSchemaRegistry)
        val producerBuilder = KafkaProducerBuilderImpl(avroSchemaRegistry)

        return KafkaEventLogSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
            processor,
            partitionAssignmentListener,
            lifecycleCoordinatorFactory
        )
    }

    override fun <K : Any, V : Any> createRandomAccessSubscription(
        subscriptionConfig: SubscriptionConfig,
        nodeConfig: SmartConfig,
        keyClass: Class<K>,
        valueClass: Class<V>
    ): RandomAccessSubscription<K, V> {
        val config = resolveSubscriptionConfiguration(
            subscriptionConfig.toConfig(),
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_RANDOMACCESS
        )
        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(avroSchemaRegistry)


        return KafkaRandomAccessSubscriptionImpl(
            config,
            consumerBuilder,
            keyClass,
            valueClass,
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
        val consumerBuilder = CordaKafkaConsumerBuilderImpl<String, RPCRequest>(avroSchemaRegistry)

        val cordaAvroSerializer = CordaAvroSerializer<RESPONSE>(avroSchemaRegistry)
        val cordaAvroDeserializer = CordaAvroDeserializer(avroSchemaRegistry, { _, _ -> }, rpcConfig.requestType)
        val publisher = publisherFactory.createPublisher(PublisherConfig(rpcConfig.clientName), nodeConfig)

        return KafkaRPCSubscriptionImpl(
            config,
            publisher,
            consumerBuilder,
            responderProcessor,
            cordaAvroSerializer,
            cordaAvroDeserializer,
            lifecycleCoordinatorFactory
        )
    }
}
