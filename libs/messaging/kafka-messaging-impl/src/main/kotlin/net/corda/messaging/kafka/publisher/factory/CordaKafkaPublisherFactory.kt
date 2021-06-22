package net.corda.messaging.kafka.publisher.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.kafka.producer.builder.impl.KafkaProducerBuilderImpl
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CLIENT_ID_COUNTER
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PATTERN_PUBLISHER
import net.corda.messaging.kafka.publisher.CordaKafkaPublisherImpl
import net.corda.messaging.kafka.toConfig
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Kafka implementation for Publisher Factory.
 * @property avroSchemaRegistry OSGi DS Injected avro schema registry
 */
@Component
class CordaKafkaPublisherFactory @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry
) : PublisherFactory {

    private val enforced = ConfigFactory.parseResourcesAnySyntax("messaging-enforced.conf")
    private val defaults = ConfigFactory.parseResourcesAnySyntax("messaging-defaults.conf")

    override fun createPublisher(
        publisherConfig: PublisherConfig,
        nodeConfig: Config
    ): Publisher {
        val config = resolveConfiguration(publisherConfig.toConfig(), nodeConfig, PATTERN_PUBLISHER)
        val producer = KafkaProducerBuilderImpl(avroSchemaRegistry).createProducer(config)
        return CordaKafkaPublisherImpl(config, producer)
    }

    private fun resolveConfiguration(
        subscriptionConfiguration: Config,
        nodeConfig: Config,
        pattern: String
    ): Config {
        return enforced
            .withFallback(subscriptionConfiguration)
            .withValue(CLIENT_ID_COUNTER, ConfigValueFactory.fromAnyRef(1))
            .withFallback(nodeConfig)
            .withFallback(defaults)
            .resolve()
            .getConfig(pattern)
    }
}
