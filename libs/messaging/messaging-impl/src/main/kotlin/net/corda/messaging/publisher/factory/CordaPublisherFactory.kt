package net.corda.messaging.publisher.factory

import net.corda.data.CordaAvroSerializationFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.config.ConfigResolver
import net.corda.messaging.publisher.CordaPublisherImpl
import net.corda.messaging.publisher.CordaRPCSenderImpl
import net.corda.messaging.subscription.consumer.builder.CordaConsumerBuilder
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.atomic.AtomicInteger

/**
 * Patterns implementation for Publisher Factory.
 */
@Component
class CordaPublisherFactory @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val avroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = CordaProducerBuilder::class)
    private val cordaProducerBuilder: CordaProducerBuilder,
    @Reference(service = CordaConsumerBuilder::class)
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : PublisherFactory {

    // Used to ensure that each subscription has a unique client.id
    private val clientIdCounter = AtomicInteger()

    override fun createPublisher(
        publisherConfig: PublisherConfig,
        kafkaConfig: SmartConfig
    ): Publisher {
        val configBuilder = ConfigResolver(kafkaConfig.factory)
        val config = configBuilder.buildPublisherConfig(publisherConfig, kafkaConfig)
        val producer = cordaProducerBuilder.createProducer(config.busConfig)
        return CordaPublisherImpl(config, producer)
    }

    override fun <REQUEST : Any, RESPONSE : Any> createRPCSender(
        rpcConfig: RPCConfig<REQUEST, RESPONSE>,
        kafkaConfig: SmartConfig
    ): RPCSender<REQUEST, RESPONSE> {

//        val publisherConfiguration = ConfigFactory.empty()
//            .withValue(GROUP, ConfigValueFactory.fromAnyRef(rpcConfig.groupName))
//            .withValue(TOPIC, ConfigValueFactory.fromAnyRef(rpcConfig.requestTopic))
//            .withValue("clientName", ConfigValueFactory.fromAnyRef(rpcConfig.clientName))
//
//        val publisherConfig = resolvePublisherConfiguration(
//            publisherConfiguration,
//            kafkaConfig,
//            clientIdCounter.getAndIncrement(),
//            PATTERN_RPC_SENDER
//        ).withoutPath(PRODUCER_TRANSACTIONAL_ID)

        val configBuilder = ConfigResolver(kafkaConfig.factory)
        val subscriptionConfig = SubscriptionConfig(rpcConfig.groupName, rpcConfig.requestTopic)
        val config =
            configBuilder.buildSubscriptionConfig(subscriptionConfig, kafkaConfig, clientIdCounter.getAndIncrement())
        val serializer = avroSerializationFactory.createAvroSerializer<REQUEST> { }
        val deserializer = avroSerializationFactory.createAvroDeserializer({}, rpcConfig.responseType)

        return CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )
    }
}
