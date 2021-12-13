package net.corda.messaging.kafka.publisher.factory

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.messaging.RPCResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.kafka.producer.builder.impl.KafkaProducerBuilderImpl
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.GROUP
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.KAFKA_PRODUCER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_PUBLISHER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_RPC_SENDER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.publisher.CordaKafkaPublisherImpl
import net.corda.messaging.kafka.publisher.CordaKafkaRPCSenderImpl
import net.corda.messaging.kafka.subscription.CordaAvroDeserializer
import net.corda.messaging.kafka.subscription.consumer.builder.impl.CordaKafkaConsumerBuilderImpl
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
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : PublisherFactory {

    // Used to ensure that each subscription has a unique client.id
    private val clientIdCounter = AtomicInteger()

    override fun createPublisher(
        publisherConfig: PublisherConfig,
        kafkaConfig: SmartConfig
    ): Publisher {
        val config = resolvePublisherConfiguration(
            publisherConfig.toConfig(),
            kafkaConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_PUBLISHER
        )
        val producer = KafkaProducerBuilderImpl(avroSchemaRegistry).createProducer(config.getConfig(KAFKA_PRODUCER))
        return CordaKafkaPublisherImpl(config, producer)
    }

    override fun <REQUEST : Any, RESPONSE : Any> createRPCSender(
        rpcConfig: RPCConfig<REQUEST, RESPONSE>,
        kafkaConfig: SmartConfig
    ): RPCSender<REQUEST, RESPONSE> {

        val publisherConfiguration = ConfigFactory.empty()
            .withValue(GROUP, ConfigValueFactory.fromAnyRef(rpcConfig.groupName))
            .withValue(TOPIC, ConfigValueFactory.fromAnyRef(rpcConfig.requestTopic))
            .withValue("clientName", ConfigValueFactory.fromAnyRef(rpcConfig.clientName))

        val publisherConfig = resolvePublisherConfiguration(
            publisherConfiguration,
            kafkaConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_RPC_SENDER
        )

        val publisher = createPublisher(PublisherConfig(rpcConfig.clientName), kafkaConfig)

        val consumerBuilder = CordaKafkaConsumerBuilderImpl<String, RPCResponse>(avroSchemaRegistry)
        val serializer = CordaAvroSerializer<REQUEST>(avroSchemaRegistry)
        val deserializer = CordaAvroDeserializer(avroSchemaRegistry, { _, _ -> }, rpcConfig.responseType)

        return CordaKafkaRPCSenderImpl(
            publisherConfig,
            publisher,
            consumerBuilder,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )
    }
}
