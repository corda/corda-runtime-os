package net.corda.messaging.publisher.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.config.MessagingConfigResolver
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.publisher.CordaPublisherImpl
import net.corda.messaging.publisher.CordaRPCSenderImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

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

    companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createPublisher(
        publisherConfig: PublisherConfig,
        messagingConfig: SmartConfig
    ): Publisher {
        val configBuilder = MessagingConfigResolver(messagingConfig.factory)
        logger.info("BOGDAN - PUBLISHER CONFIG BEFORE MERGING IS $publisherConfig")
        val config = configBuilder.buildPublisherConfig(publisherConfig, messagingConfig)
        logger.info("BOGDAN - CREATING PUBLISHER WITH CONFIG $config")
        // TODO 3781 - topic prefix
        val producerConfig = ProducerConfig(config.clientId, config.instanceId, config.transactional, ProducerRoles.PUBLISHER)
        return CordaPublisherImpl(config, producerConfig, cordaProducerBuilder)
    }

    override fun <REQUEST : Any, RESPONSE : Any> createRPCSender(
        rpcConfig: RPCConfig<REQUEST, RESPONSE>,
        messagingConfig: SmartConfig
    ): RPCSender<REQUEST, RESPONSE> {

        val configResolver = MessagingConfigResolver(messagingConfig.factory)
        val subscriptionConfig = SubscriptionConfig(rpcConfig.groupName, rpcConfig.requestTopic)
        val config = configResolver.buildSubscriptionConfig(
            SubscriptionType.RPC_SENDER,
            subscriptionConfig,
            messagingConfig,
            UUID.randomUUID().toString()
        )
        val serializer = avroSerializationFactory.createAvroSerializer<REQUEST> { }
        val deserializer = avroSerializationFactory.createAvroDeserializer({}, rpcConfig.responseType)

        return CordaRPCSenderImpl(
            config = config,
            cordaConsumerBuilder = cordaConsumerBuilder,
            cordaProducerBuilder = cordaProducerBuilder,
            serializer = serializer,
            deserializer = deserializer,
            lifecycleCoordinatorFactory = lifecycleCoordinatorFactory
        )
    }
}
