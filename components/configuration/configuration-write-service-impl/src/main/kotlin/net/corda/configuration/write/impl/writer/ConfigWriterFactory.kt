package net.corda.configuration.write.impl.writer

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Config.Companion.CONFIG_MGMT_REQUEST_TOPIC
import javax.persistence.EntityManagerFactory

/** A factory for [ConfigWriter]s. */
internal class ConfigWriterFactory(
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory
) {
    /**
     * Creates a [ConfigWriter].
     *
     * @param config Config to be used by the subscription.
     * @param instanceId The instance ID to use for subscribing to Kafka.
     * @param entityManagerFactory The factory to be used by the config writer to create entity managers.
     *
     * @throws ConfigWriterException If the required Kafka publishers and subscriptions cannot be set up.
     */
    internal fun create(
        config: SmartConfig,
        instanceId: Int,
        entityManagerFactory: EntityManagerFactory
    ): ConfigWriter {
        val publisher = createPublisher(config, instanceId)
        val subscription = createRPCSubscription(config, publisher, entityManagerFactory)
        return ConfigWriter(subscription, publisher)
    }

    /**
     * Creates a [Publisher] using the provided [config] and [instanceId].
     *
     * @throws ConfigWriterException If the publisher cannot be set up.
     */
    private fun createPublisher(config: SmartConfig, instanceId: Int): Publisher {
        val publisherConfig = PublisherConfig(CLIENT_NAME_DB, instanceId)
        return try {
            publisherFactory.createPublisher(publisherConfig, config)
        } catch (e: Exception) {
            throw ConfigWriterException("Could not create publisher to publish updated configuration.", e)
        }
    }

    /**
     * Creates a [ConfigurationManagementRPCSubscription] using the provided [config]. The subscription is for the
     * [CONFIG_MGMT_REQUEST_TOPIC] topic, and handles requests using a [ConfigWriterProcessor].
     *
     * @throws ConfigWriterException If the subscription cannot be set up.
     */
    private fun createRPCSubscription(
        config: SmartConfig,
        publisher: Publisher,
        entityManagerFactory: EntityManagerFactory
    ): ConfigurationManagementRPCSubscription {

        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            CONFIG_MGMT_REQUEST_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java,
        )
        val configEntityRepository = ConfigEntityRepository(entityManagerFactory)
        val processor = ConfigWriterProcessor(publisher, configEntityRepository)

        return try {
            subscriptionFactory.createRPCSubscription(rpcConfig, config, processor)
        } catch (e: Exception) {
            throw ConfigWriterException("Could not create subscription to process configuration update requests.", e)
        }
    }
}
