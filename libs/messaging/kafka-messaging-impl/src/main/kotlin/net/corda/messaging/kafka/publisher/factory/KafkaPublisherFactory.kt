package net.corda.messaging.kafka.publisher.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.kafka.publisher.KafkaPublisher
import net.corda.messaging.kafka.producer.builder.impl.KafkaProducerBuilder
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CONF_PREFIX
import org.apache.kafka.clients.producer.ProducerConfig
import org.osgi.service.component.annotations.Component
import java.util.Properties

/**
 * Kafka implementation for Publisher Factory.
 */
@Component
class KafkaPublisherFactory : PublisherFactory {

    override fun <K, V> createPublisher(
        publisherConfig: PublisherConfig,
        properties: Map<String, String>
    ): Publisher<K, V> {
        //TODO - replace this with a  call to OSGi ConfigService, possibly multiple configs required
        val defaultKafkaConfig = ConfigFactory.load("tmpKafkaDefaults")
        val producerProperties = getProducerProps(publisherConfig, defaultKafkaConfig, properties)
        val producer = KafkaProducerBuilder<K, V>().createProducer(defaultKafkaConfig, producerProperties, publisherConfig)

        return KafkaPublisher(publisherConfig, producer)
    }

    /**
     * Generate producer properties with default values from [defaultKafkaConfig] unless overridden by the given [overrideProperties].
     * @param publisherConfig Publisher config
     * @param defaultKafkaConfig Default Producer config
     * @param overrideProperties Properties to override default config.
     * @return Kafka Producer properties.
     */
    private fun getProducerProps(publisherConfig: PublisherConfig, defaultKafkaConfig: Config,
                                 overrideProperties: Map<String, String>): Properties {
        val properties = Properties()
        properties.putAll(overrideProperties)
        val conf: Config = ConfigFactory.parseProperties(properties).withFallback(defaultKafkaConfig)
        val producerProps = Properties()

        //Could do something smarter here like
        //Store all kafka producer props in typesafeConf as "kafka.producer.props"
        //read all values from conf with a prefix of "kafka.producer.props"
        //or store all producer defaults in their own typesafeconfig
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
