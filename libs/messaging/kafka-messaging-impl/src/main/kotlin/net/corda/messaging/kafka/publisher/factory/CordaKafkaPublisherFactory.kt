package net.corda.messaging.kafka.publisher.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.kafka.mergeProperties
import net.corda.messaging.kafka.producer.builder.impl.KafkaProducerBuilderImpl
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CONF_PREFIX
import net.corda.messaging.kafka.properties.PublisherConfigProperties.Companion.PUBLISHER_CLIENT_ID
import net.corda.messaging.kafka.properties.PublisherConfigProperties.Companion.PUBLISHER_INSTANCE_ID
import net.corda.messaging.kafka.publisher.CordaKafkaPublisherImpl
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.clients.producer.ProducerConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.Properties

/**
 * Kafka implementation for Publisher Factory.
 * @property avroSchemaRegistry OSGi DS Injected avro schema registry
 */
@Component
class CordaKafkaPublisherFactory @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry) : PublisherFactory {

    override fun createPublisher(
        publisherConfig: PublisherConfig,
        properties: Map<String, String>
    ): Publisher {
        //TODO - replace this with a  call to OSGi ConfigService, possibly multiple configs required
        val defaultKafkaConfig = ConfigFactory.load("tmpKafkaDefaults")
        var config = defaultKafkaConfig
            .withValue(PUBLISHER_CLIENT_ID, ConfigValueFactory.fromAnyRef(publisherConfig.clientId))

        val instanceId = publisherConfig.instanceId
        if (instanceId != null) {
            config = config.withValue(PUBLISHER_INSTANCE_ID, ConfigValueFactory.fromAnyRef(instanceId))
        }

        val producerProperties = getProducerProps(config, properties)
        val producer = KafkaProducerBuilderImpl(config, avroSchemaRegistry, producerProperties).createProducer()

        return CordaKafkaPublisherImpl(publisherConfig, defaultKafkaConfig, producer)
    }

    /**
     * Generate producer properties with default values from [config] unless overridden by the given [overrideProperties].
     * @param config config
     * @param overrideProperties Properties to override default config.
     * @return Kafka Producer properties.
     */
    private fun getProducerProps(config: Config,
                                 overrideProperties: Map<String, String>): Properties {
        val clientId = config.getString(PUBLISHER_CLIENT_ID)
        val instanceId = if (config.hasPath(PUBLISHER_INSTANCE_ID)) config.getString(PUBLISHER_INSTANCE_ID) else null

        //TODO - update the below when config task  has evolved
        val producerProps = mergeProperties(config, PRODUCER_CONF_PREFIX, overrideProperties)
        producerProps[ProducerConfig.CLIENT_ID_CONFIG] = clientId

        if (instanceId != null) {
            producerProps[ProducerConfig.TRANSACTIONAL_ID_CONFIG] =
                "publishing-producer-$clientId-$instanceId"
        }

        return producerProps
    }
}
