package net.corda.messaging.kafka.subscription.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
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
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.mergeProperties
import net.corda.messaging.kafka.producer.builder.impl.KafkaProducerBuilderImpl
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_CONF_PREFIX
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CONF_PREFIX
import net.corda.messaging.kafka.properties.PublisherConfigProperties
import net.corda.messaging.kafka.subscription.KafkaCompactedSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaDurableSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaPubSubSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaStateAndEventSubscriptionImpl
import net.corda.messaging.kafka.subscription.asEventSubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.builder.impl.CordaKafkaConsumerBuilderImpl
import net.corda.messaging.kafka.subscription.consumer.builder.impl.StateAndEventConsumerBuilderImpl
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

/**
 * Kafka implementation of the Subscription Factory.
 * @property avroSchemaRegistry OSGi DS Injected avro schema registry
 */
@Component
class KafkaSubscriptionFactory @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry
) : SubscriptionFactory {

    companion object {
        private const val ISOLATION_LEVEL_READ_COMMITTED = "read_committed"
        private const val AUTO_OFFSET_RESET_LATEST = "latest"
        private const val AUTO_OFFSET_RESET_EARLIEST = "earliest"
        private const val FALSE = "false"
    }

    override fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        properties: Map<String, String>
    ): Subscription<K, V> {

        //pattern specific properties
        val overrideProperties = properties.toMutableMap()
        overrideProperties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = FALSE
        overrideProperties[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = ISOLATION_LEVEL_READ_COMMITTED
        overrideProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = AUTO_OFFSET_RESET_LATEST

        //TODO - replace this with a  call to OSGi ConfigService, possibly multiple configs required
        val defaultKafkaConfig = ConfigFactory.load("tmpKafkaDefaults")

        val consumerProperties = getConsumerProps(subscriptionConfig, defaultKafkaConfig, overrideProperties)
        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(defaultKafkaConfig, consumerProperties, avroSchemaRegistry)
        return KafkaPubSubSubscriptionImpl(subscriptionConfig, defaultKafkaConfig, consumerBuilder, processor, executor)
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        properties: Map<String, String>,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        //TODO - replace this with a  call to OSGi ConfigService

        val publisherClientId = "durableSub-${subscriptionConfig.groupName}-producer"
        val config = ConfigFactory.load("tmpKafkaDefaults")
            .withValue(
                PublisherConfigProperties.PUBLISHER_CLIENT_ID,
                ConfigValueFactory.fromAnyRef(publisherClientId)
            )
            .withValue(
                PublisherConfigProperties.PUBLISHER_INSTANCE_ID,
                ConfigValueFactory.fromAnyRef(subscriptionConfig.instanceId)
            )

        //pattern specific properties
        val consumerOverrideProperties = properties.toMutableMap()
        consumerOverrideProperties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = FALSE
        consumerOverrideProperties[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = ISOLATION_LEVEL_READ_COMMITTED
        consumerOverrideProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = AUTO_OFFSET_RESET_EARLIEST
        val consumerProperties = getConsumerProps(subscriptionConfig, config, consumerOverrideProperties)
        val producerProperties = getProducerProps(config, emptyMap())

        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(config, consumerProperties, avroSchemaRegistry)
        val producerBuilder = KafkaProducerBuilderImpl(config, avroSchemaRegistry, producerProperties)
        return KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder, processor)
    }

    override fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        properties: Map<String, String>
    ): CompactedSubscription<K, V> {
        // pattern specific properties
        val overrideProperties = properties.toMutableMap()
        overrideProperties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = FALSE
        overrideProperties[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = ISOLATION_LEVEL_READ_COMMITTED
        overrideProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = AUTO_OFFSET_RESET_EARLIEST

        //TODO - replace this with a  call to OSGi ConfigService, possibly multiple configs required
        val defaultKafkaConfig = ConfigFactory.load("tmpKafkaDefaults")

        val mapFactory = object : SubscriptionMapFactory<K, V> {
            override fun createMap(): MutableMap<K, V> = ConcurrentHashMap<K, V>()

            override fun destroyMap(map: MutableMap<K, V>) {
                map.clear()
            }
        }

        val consumerProperties = getConsumerProps(subscriptionConfig, defaultKafkaConfig, overrideProperties)
        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(defaultKafkaConfig, consumerProperties, avroSchemaRegistry)
        return KafkaCompactedSubscriptionImpl(
            subscriptionConfig,
            defaultKafkaConfig,
            mapFactory,
            consumerBuilder,
            processor
        )
    }

    override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: StateAndEventSubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        properties: Map<String, String>
    ): StateAndEventSubscription<K, S, E> {
        //TODO - replace this with a  call to OSGi ConfigService

        val publisherClientId = "stateAndEvent-${subscriptionConfig.groupName}-producer"
        val config = ConfigFactory.load("tmpKafkaDefaults")
            .withValue(
                PublisherConfigProperties.PUBLISHER_CLIENT_ID,
                ConfigValueFactory.fromAnyRef(publisherClientId)
            )
            .withValue(
                PublisherConfigProperties.PUBLISHER_INSTANCE_ID,
                ConfigValueFactory.fromAnyRef(subscriptionConfig.instanceId)
            )

        //pattern specific properties
        val consumerOverrideProperties = properties.toMutableMap()
        consumerOverrideProperties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = FALSE
        consumerOverrideProperties[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = ISOLATION_LEVEL_READ_COMMITTED
        consumerOverrideProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = AUTO_OFFSET_RESET_EARLIEST
        val consumerProperties = getConsumerProps(
            subscriptionConfig.asEventSubscriptionConfig(),
            config,
            consumerOverrideProperties
        )
        val producerProperties = getProducerProps(config, emptyMap())

        val eventConsumerBuilder = CordaKafkaConsumerBuilderImpl<K, E>(
            config,
            consumerProperties,
            avroSchemaRegistry
        )
        val stateConsumerBuilder = CordaKafkaConsumerBuilderImpl<K, S>(
            config,
            consumerProperties,
            avroSchemaRegistry
        )
        val stateAndEventConsumerBuilder = StateAndEventConsumerBuilderImpl(
            stateConsumerBuilder,
            eventConsumerBuilder,
            subscriptionConfig
        )

        val mapFactory = object : SubscriptionMapFactory<K, Pair<Long, S>> {
            override fun createMap(): MutableMap<K, Pair<Long, S>> = ConcurrentHashMap<K, Pair<Long, S>>()

            override fun destroyMap(map: MutableMap<K, Pair<Long, S>>) {
                map.clear()
            }
        }


        val producerBuilder = KafkaProducerBuilderImpl(config, avroSchemaRegistry, producerProperties)
        return KafkaStateAndEventSubscriptionImpl(
            subscriptionConfig,
            config,
            mapFactory,
            stateAndEventConsumerBuilder,
            producerBuilder,
            processor
        )
    }

    override fun <K : Any, V : Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        properties: Map<String, String>,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> createRandomAccessSubscription(
        subscriptionConfig: SubscriptionConfig,
        properties: Map<String, String>
    ): RandomAccessSubscription<K, V> {
        TODO("Not yet implemented")
    }

    /**
     * Generate consumer properties with default values from [subscriptionConfig] and [defaultKafkaConfig]
     * unless overridden by the given [overrideProperties].
     * @return Kafka Consumer properties
     */
    private fun getConsumerProps(
        subscriptionConfig: SubscriptionConfig,
        config: Config,
        overrideProperties: Map<String, String>
    ): Properties {
        //TODO - update the below when config task has evolved
        val consumerProps = mergeProperties(config, CONSUMER_CONF_PREFIX, overrideProperties)
        consumerProps[ConsumerConfig.GROUP_ID_CONFIG] = subscriptionConfig.groupName
        consumerProps[ConsumerConfig.CLIENT_ID_CONFIG] = Instant.now().toString()
        return consumerProps
    }

    /**
     * Generate producer properties from the [config] applying [overrideProperties] from the factory.
     *
     */
    private fun getProducerProps(config: Config, overrideProperties: Map<String, String>): Properties {
        //TODO - update the below when config task  has evolved
        val producerClientId = config.getString(PublisherConfigProperties.PUBLISHER_CLIENT_ID)
        val instanceId = if (config.hasPath(PublisherConfigProperties.PUBLISHER_INSTANCE_ID)) config.getInt(
            PublisherConfigProperties.PUBLISHER_INSTANCE_ID
        ) else throw CordaMessageAPIFatalException("Cannot create subscription producer $producerClientId. No instanceId configured")

        val producerProps = mergeProperties(config, PRODUCER_CONF_PREFIX, overrideProperties)
        producerProps[ProducerConfig.CLIENT_ID_CONFIG] = producerClientId
        producerProps[ProducerConfig.TRANSACTIONAL_ID_CONFIG] = "$producerClientId-$instanceId"

        return producerProps
    }
}
