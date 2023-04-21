package net.corda.messaging.publisher.factory

import net.corda.data.CordaAvroSerializationFactory
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
import java.util.concurrent.atomic.AtomicLong

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
    private val clientIdCounter = AtomicLong()

    override fun createPublisher(
        publisherConfig: PublisherConfig,
        messagingConfig: SmartConfig
    ): Publisher {
        val configBuilder = MessagingConfigResolver(messagingConfig.factory)
        val config = configBuilder.buildPublisherConfig(publisherConfig, messagingConfig)
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
            clientIdCounter.getAndIncrement()
        )
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
