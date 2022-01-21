package net.corda.libs.virtualnode.write.impl

import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.write.VirtualNodeWriter
import net.corda.libs.virtualnode.write.VirtualNodeWriterException
import net.corda.libs.virtualnode.write.VirtualNodeWriterFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Config.Companion.CONFIG_MGMT_REQUEST_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_CREATION_REQUEST_TOPIC
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** An implementation of [VirtualNodeWriterFactory]. */
@Suppress("Unused")
@Component(service = [VirtualNodeWriterFactory::class])
internal class VirtualNodeWriterFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : VirtualNodeWriterFactory {

    override fun create(
        config: SmartConfig,
        instanceId: Int
    ): VirtualNodeWriter {
        val publisher = createPublisher(config, instanceId)
        val subscription = createRPCSubscription(config, publisher)
        return VirtualNodeWriterImpl(subscription, publisher)
    }

    /**
     * Creates a [Publisher] using the provided [config] and [instanceId].
     *
     * @throws VirtualNodeWriterException If the publisher cannot be set up.
     */
    private fun createPublisher(config: SmartConfig, instanceId: Int): Publisher {
        val publisherConfig = PublisherConfig(CLIENT_NAME_DB, instanceId)
        return try {
            publisherFactory.createPublisher(publisherConfig, config)
        } catch (e: Exception) {
            throw VirtualNodeWriterException("Could not create publisher to publish updated configuration.", e)
        }
    }

    /**
     * Creates a [ConfigurationManagementRPCSubscription] using the provided [config]. The subscription is to the
     * [CONFIG_MGMT_REQUEST_TOPIC] topic, and handles requests using a [VirtualNodeWriterProcessor].
     *
     * @throws VirtualNodeWriterException If the subscription cannot be set up.
     */
    private fun createRPCSubscription(
        config: SmartConfig,
        publisher: Publisher
    ): ConfigurationManagementRPCSubscription {

        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
            VirtualNodeCreationRequest::class.java,
            VirtualNodeCreationResponse::class.java,
        )
        val processor = VirtualNodeWriterProcessor(publisher)

        return try {
            subscriptionFactory.createRPCSubscription(rpcConfig, config, processor)
        } catch (e: Exception) {
            throw VirtualNodeWriterException("Could not create subscription to process configuration update requests.", e)
        }
    }
}
