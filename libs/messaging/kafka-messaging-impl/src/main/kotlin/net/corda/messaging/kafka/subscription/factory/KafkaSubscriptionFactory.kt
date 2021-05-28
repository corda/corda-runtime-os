package net.corda.messaging.kafka.subscription.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_CONF_PREFIX
import net.corda.messaging.kafka.subscription.KafkaCompactedSubscriptionImpl
import net.corda.messaging.kafka.subscription.KafkaPubSubSubscriptionImpl
import net.corda.messaging.kafka.properties.PublisherConfigProperties
import net.corda.messaging.kafka.subscription.consumer.builder.impl.CordaKafkaConsumerBuilderImpl
import net.corda.messaging.kafka.subscription.KafkaDurableSubscriptionImpl
import net.corda.messaging.kafka.subscription.producer.builder.impl.SubscriptionProducerBuilderImpl
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.Properties
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
): SubscriptionFactory {

    companion object {
        private const val ISOLATION_LEVEL_READ_COMMITTED = "read_committed"
        private const val AUTO_OFFSET_RESET_LATEST = "latest"
        private const val AUTO_OFFSET_RESET_EARLIEST = "earliest"
        private const val TRUE = "true"
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
        overrideProperties[CONSUMER_CONF_PREFIX + ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = FALSE
        overrideProperties[CONSUMER_CONF_PREFIX + ConsumerConfig.ISOLATION_LEVEL_CONFIG] = ISOLATION_LEVEL_READ_COMMITTED
        overrideProperties[CONSUMER_CONF_PREFIX + ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = AUTO_OFFSET_RESET_LATEST

        //TODO - replace this with a  call to OSGi ConfigService, possibly multiple configs required
        val defaultKafkaConfig = ConfigFactory.load("tmpKafkaDefaults")

        val consumerProperties = getConsumerProps(subscriptionConfig, defaultKafkaConfig, overrideProperties)
        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(defaultKafkaConfig, consumerProperties, avroSchemaRegistry)
        return KafkaPubSubSubscriptionImpl(subscriptionConfig, defaultKafkaConfig, consumerBuilder, processor, executor)
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        properties: Map<String, String>
    ): Subscription<K, V> {
        //TODO - replace this with a  call to OSGi ConfigService

        val publisherClientId = "${subscriptionConfig.groupName}-producer"
        val config = ConfigFactory.load("tmpKafkaDefaults")
            .withValue(PublisherConfigProperties.PUBLISHER_CLIENT_ID,
                ConfigValueFactory.fromAnyRef(publisherClientId))
            .withValue(PublisherConfigProperties.PUBLISHER_INSTANCE_ID,
                ConfigValueFactory.fromAnyRef("durablesub-$publisherClientId"))

        //pattern specific properties
        val overrideProperties = properties.toMutableMap()
        overrideProperties[CONSUMER_CONF_PREFIX + ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = FALSE
        overrideProperties[CONSUMER_CONF_PREFIX + ConsumerConfig.ISOLATION_LEVEL_CONFIG] = ISOLATION_LEVEL_READ_COMMITTED
        overrideProperties[CONSUMER_CONF_PREFIX + ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = AUTO_OFFSET_RESET_EARLIEST
        val consumerProperties = getConsumerProps(subscriptionConfig, config, overrideProperties)
        val producerProperties = getProducerProps(config, overrideProperties)

        val consumerBuilder = CordaKafkaConsumerBuilderImpl<K, V>(config, consumerProperties, avroSchemaRegistry)
        val producerBuilder = SubscriptionProducerBuilderImpl(config, avroSchemaRegistry, producerProperties)
        return KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder, processor)
    }

    override fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        properties: Map<String, String>
    ): CompactedSubscription<K, V> {
        // pattern specific properties
        val overrideProperties = properties.toMutableMap()
        overrideProperties[CONSUMER_CONF_PREFIX + ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = FALSE
        overrideProperties[CONSUMER_CONF_PREFIX + ConsumerConfig.ISOLATION_LEVEL_CONFIG] = ISOLATION_LEVEL_READ_COMMITTED
        overrideProperties[CONSUMER_CONF_PREFIX + ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = AUTO_OFFSET_RESET_EARLIEST

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
        return KafkaCompactedSubscriptionImpl(subscriptionConfig, defaultKafkaConfig, mapFactory, consumerBuilder, processor)
    }

    override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: StateAndEventSubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        properties: Map<String, String>
    ): StateAndEventSubscription<K, S, E> {
        TODO("Not yet implemented")
    }

    /**
     * Generate consumer properties with default values from [subscriptionConfig] and [defaultKafkaConfig]
     * unless overridden by the given [overrideProperties].
     * @return Kafka Consumer properties
     */
    private fun getConsumerProps(subscriptionConfig: SubscriptionConfig, defaultKafkaConfig: Config,
                                 overrideProperties: Map<String, String>): Properties {
        val properties = Properties()
        properties.putAll(overrideProperties)
        val conf: Config = ConfigFactory.parseProperties(properties).withFallback(defaultKafkaConfig)
        val consumerProps = Properties()

        consumerProps[ConsumerConfig.GROUP_ID_CONFIG] = subscriptionConfig.groupName

        //TODO - update the below when config task has evolved
        consumerProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)
        consumerProps[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.MAX_POLL_RECORDS_CONFIG)
        consumerProps[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG)
        consumerProps[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)
        consumerProps[ConsumerConfig.ISOLATION_LEVEL_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.ISOLATION_LEVEL_CONFIG)
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)
        return consumerProps
    }

    /**
     * Generate producer properties from the [config] applying [overrideProperties] from the factory.
     *
     */
    private fun getProducerProps(config: Config, overrideProperties: Map<String, String>): Properties {
        //TODO - update the below when config task  has evolved
        val properties = Properties()
        properties.putAll(overrideProperties)
        val conf: Config = ConfigFactory.parseProperties(properties).withFallback(config)
        val producerClientId = config.getString(PublisherConfigProperties.PUBLISHER_CLIENT_ID)

        val producerProps = Properties()
        producerProps[ProducerConfig.CLIENT_ID_CONFIG] = producerClientId

        producerProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] =
            conf.getString(KafkaProperties.PRODUCER_CONF_PREFIX + ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)
        producerProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] =
            conf.getString(KafkaProperties.PRODUCER_CONF_PREFIX + ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)
        //TODO - append unique id for this node instance + node identity to this so it is unique and deterministic
        // across multiple HA nodes and instance restarts
        producerProps[ProducerConfig.TRANSACTIONAL_ID_CONFIG] = producerClientId

        return producerProps
    }
}
