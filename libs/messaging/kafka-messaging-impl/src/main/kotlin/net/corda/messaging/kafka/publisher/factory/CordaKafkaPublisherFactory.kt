package net.corda.messaging.kafka.publisher.factory

import com.typesafe.config.Config
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.kafka.producer.builder.impl.KafkaProducerBuilderImpl
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.KAFKA_PRODUCER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_PUBLISHER
import net.corda.messaging.kafka.publisher.CordaKafkaPublisherImpl
import net.corda.messaging.kafka.utils.ConfigUtils.Companion.resolvePublisherConfiguration
import net.corda.messaging.kafka.utils.toConfig
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kafka implementation for Publisher Factory.
 * @property avroSchemaRegistry OSGi DS Injected avro schema registry
 */
@Component
class CordaKafkaPublisherFactory @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry
) : PublisherFactory {

    // Used to ensure that each subscription has a unique client.id
    private val clientIdCounter = AtomicInteger()

    override fun createPublisher(
        publisherConfig: PublisherConfig,
        nodeConfig: Config
    ): Publisher {
        val config = resolvePublisherConfiguration(
            publisherConfig.toConfig(),
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_PUBLISHER
        )
        val producer = KafkaProducerBuilderImpl(avroSchemaRegistry).createProducer(config.getConfig(KAFKA_PRODUCER))
        return CordaKafkaPublisherImpl(config, producer)
    }

    override fun <TREQ : Any, TRESP : Any> createRPCSender(
        rpcConfig: RPCConfig<TREQ, TRESP>,
        nodeConfig: Config
    ): RPCSender<TREQ, TRESP> {
        TODO("Not yet implemented")
    }
}
