package net.corda.messaging.kafka.publisher.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.kafka.producer.builder.impl.KafkaProducerBuilderImpl
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_PRODUCER
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PATTERN_PUBLISHER
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PATTERN_RPC
import net.corda.messaging.kafka.publisher.CordaKafkaPublisherImpl
import net.corda.messaging.kafka.publisher.CordaKafkaRPCSenderImpl
import net.corda.messaging.kafka.subscription.KafkaRPCSubscription
import net.corda.messaging.kafka.subscription.consumer.builder.impl.CordaKafkaConsumerBuilderImpl
import net.corda.messaging.kafka.utils.ConfigUtils.Companion.resolvePublisherConfiguration
import net.corda.messaging.kafka.utils.ConfigUtils.Companion.resolveSubscriptionConfiguration
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
        nodeConfig: Config,
        responderProcessor: RPCResponderProcessor<TREQ, TRESP>
    ): RPCSender<TREQ, TRESP> {

        val publisherConfiguration = ConfigFactory.empty()
            .withValue(KafkaProperties.GROUP, ConfigValueFactory.fromAnyRef(rpcConfig.groupName))
            .withValue(KafkaProperties.TOPIC, ConfigValueFactory.fromAnyRef(rpcConfig.requestTopic))
            .withValue("clientName", ConfigValueFactory.fromAnyRef(rpcConfig.clientName))
            .withValue("REQUEST_TYPE", ConfigValueFactory.fromAnyRef(rpcConfig.requestType))
            .withValue("RESPONSE_TYPE", ConfigValueFactory.fromAnyRef(rpcConfig.responseType))

        val publisherConfig = resolvePublisherConfiguration(
            publisherConfiguration,
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_PUBLISHER
        )

        val consumerConfiguration = ConfigFactory.empty()
            .withValue(KafkaProperties.GROUP, ConfigValueFactory.fromAnyRef(rpcConfig.groupName))
            .withValue(KafkaProperties.TOPIC, ConfigValueFactory.fromAnyRef(rpcConfig.requestTopic + ".resp"))
            .withValue("clientName", ConfigValueFactory.fromAnyRef(rpcConfig.clientName))
            .withValue("REQUEST_TYPE", ConfigValueFactory.fromAnyRef(rpcConfig.requestType))
            .withValue("RESPONSE_TYPE", ConfigValueFactory.fromAnyRef(rpcConfig.responseType))

        val consumerConfig = resolveSubscriptionConfiguration(
            consumerConfiguration,
            nodeConfig,
            clientIdCounter.getAndIncrement(),
            PATTERN_RPC
        )

        val publisher = createPublisher(PublisherConfig(rpcConfig.clientName), nodeConfig)

            KafkaProducerBuilderImpl(avroSchemaRegistry).createProducer(publisherConfig.getConfig(KAFKA_PRODUCER))
        val rpcSubscription =
            KafkaRPCSubscription(
                rpcConfig,
                consumerConfig,
                CordaKafkaConsumerBuilderImpl(avroSchemaRegistry),
                responderProcessor
            )

        return CordaKafkaRPCSenderImpl(publisherConfig, publisher, rpcSubscription)
    }
}
