package net.corda.messaging.kafka.publisher.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.kafka.producer.builder.impl.KafkaProducerBuilder
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CONF_PREFIX
import net.corda.messaging.kafka.properties.PublisherConfigProperties.Companion.PUBLISHER_CLIENT_ID
import net.corda.messaging.kafka.properties.PublisherConfigProperties.Companion.PUBLISHER_INSTANCE_ID
import net.corda.messaging.kafka.properties.PublisherConfigProperties.Companion.PUBLISHER_TOPIC
import net.corda.messaging.kafka.publisher.CordaKafkaPublisher
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.clients.producer.ProducerConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.util.Properties

/**
 * Kafka implementation for Publisher Factory.
 * @property avroSchemaRegistry OSGi DS Injected avro schema registry
 */
@Component
class CordaKafkaPublisherFactory @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry) : PublisherFactory {

    override fun <K : Any, V : Any> createPublisher(
        publisherConfig: PublisherConfig,
        properties: Map<String, String>,
        keyClass: Class<K>,
        valueClass: Class<V>
    ): Publisher<K, V> {
        //Only allow Strings as Keys for now. See CORE-1367
        if (keyClass != String::class.java) {
            throw CordaMessageAPIFatalException("Unsupported Key type, use a String.")
        }

        //TODO - replace this with a  call to OSGi ConfigService, possibly multiple configs required
        val defaultKafkaConfig = ConfigFactory.load("tmpKafkaDefaults")
        var config = defaultKafkaConfig.withValue(PUBLISHER_CLIENT_ID, ConfigValueFactory.fromAnyRef(publisherConfig.clientId))
            .withValue(PUBLISHER_TOPIC, ConfigValueFactory.fromAnyRef(publisherConfig.topic))

        val instanceId = publisherConfig.instanceId
        if (instanceId != null) {
            config.withValue(PUBLISHER_INSTANCE_ID, ConfigValueFactory.fromAnyRef(instanceId))
        }

        val producerProperties = getProducerProps(publisherConfig, config, properties)
        val producer = KafkaProducerBuilder<K, ByteBuffer>().createProducer(config, producerProperties)

        return CordaKafkaPublisher(publisherConfig, defaultKafkaConfig, producer, avroSchemaRegistry)
    }

    /**
     * Generate producer properties with default values from [defaultKafkaConfig] unless overridden by the given [overrideProperties].
     * @param publisherConfig Publisher config
     * @param defaultKafkaConfig Default kafka config
     * @param overrideProperties Properties to override default config.
     * @return Kafka Producer properties.
     */
    private fun getProducerProps(publisherConfig: PublisherConfig, defaultKafkaConfig: Config,
                                 overrideProperties: Map<String, String>): Properties {
        val properties = Properties()
        properties.putAll(overrideProperties)
        val conf: Config = ConfigFactory.parseProperties(properties).withFallback(defaultKafkaConfig)
        val producerProps = Properties()

        //TODO - update the below when config task  has evolved
        producerProps[ProducerConfig.CLIENT_ID_CONFIG] = publisherConfig.clientId

        producerProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] =
            conf.getString(PRODUCER_CONF_PREFIX + ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)
        producerProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] =
            conf.getString(PRODUCER_CONF_PREFIX + ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)
        producerProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] =
            conf.getString(PRODUCER_CONF_PREFIX + ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)
        producerProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] =
            conf.getString(PRODUCER_CONF_PREFIX + ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)

        if (publisherConfig.instanceId != null) {
            producerProps[ProducerConfig.TRANSACTIONAL_ID_CONFIG] =
                "publishing-producer-${publisherConfig.clientId}-${publisherConfig.topic}-${publisherConfig.instanceId}"
        }

        return producerProps
    }
}
