package net.corda.messaging.kafka.subscription.factory

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.RandomAccessSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.Utils.Companion.resolveSubscriptionConfiguration
import net.corda.messaging.kafka.producer.builder.impl.KafkaProducerBuilderImpl
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PATTERN_COMPACTED
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PATTERN_DURABLE
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PATTERN_PUBSUB
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PATTERN_STATEANDEVENT
import net.corda.messaging.kafka.subscription.KafkaCompactedSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaDurableSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaPubSubSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaStateAndEventSubscriptionImpl
import net.corda.messaging.kafka.subscription.consumer.builder.impl.CordaKafkaConsumerBuilderImpl
import net.corda.messaging.kafka.subscription.consumer.builder.impl.StateAndEventBuilderImpl
import net.corda.messaging.kafka.toConfig
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
@Component
class KafkaSubscriptionFactory @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry
) : SubscriptionFactory {

    // Used to ensure that each subscription has a unique client.id
    private val clientIdCounter = AtomicInteger()

    override fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        nodeConfig: Config
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
            executor
        )
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        nodeConfig: Config,
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
            processor
        )
    }

    override fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        nodeConfig: Config
    ): CompactedSubscription<K, V> {
        val config = resolveSubscriptionConfiguration(
            subscriptionConfig.toConfig(),
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_COMPACTED
        )
        val mapFactory = object : SubscriptionMapFactory<K, V> {
            override fun createMap(): MutableMap<K, V> = ConcurrentHashMap<K, V>()
            override fun destroyMap(map: MutableMap<K, V>) { map.clear() }
        }
        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(avroSchemaRegistry)

        return KafkaCompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
            processor
        )
    }

    override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        nodeConfig: Config
    ): StateAndEventSubscription<K, S, E> {

        val subscriptionConfiguration = subscriptionConfig.toConfig()
        val config = resolveSubscriptionConfiguration(
            subscriptionConfiguration,
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_STATEANDEVENT
        )
        val producerBuilder = KafkaProducerBuilderImpl(avroSchemaRegistry)
        val eventConsumerBuilder = CordaKafkaConsumerBuilderImpl<K, E>(avroSchemaRegistry)
        val stateConsumerBuilder = CordaKafkaConsumerBuilderImpl<K, S>(avroSchemaRegistry)

        val stateAndEventBuilder = StateAndEventBuilderImpl(
            stateConsumerBuilder,
            eventConsumerBuilder,
            producerBuilder,
        )

        val mapFactory = object : SubscriptionMapFactory<K, Pair<Long, S>> {
            override fun createMap(): MutableMap<K, Pair<Long, S>> = ConcurrentHashMap<K, Pair<Long, S>>()
            override fun destroyMap(map: MutableMap<K, Pair<Long, S>>) = map.clear()
        }

        return KafkaStateAndEventSubscriptionImpl(
            config,
            mapFactory,
            stateAndEventBuilder,
            processor
        )
    }

    override fun <K : Any, V : Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        nodeConfig: Config,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> createRandomAccessSubscription(
        subscriptionConfig: SubscriptionConfig,
        nodeConfig: Config
    ): RandomAccessSubscription<K, V> {
        TODO("Not yet implemented")
    }
}
