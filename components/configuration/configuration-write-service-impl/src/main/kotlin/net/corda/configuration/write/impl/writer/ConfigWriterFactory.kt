package net.corda.configuration.write.impl.writer

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Config.Companion.CONFIG_MGMT_REQUEST_TOPIC

/** A factory for [ConfigWriter]s. */
internal class ConfigWriterFactory(
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val configValidatorFactory: ConfigurationValidatorFactory,
    private val dbConnectionManager: DbConnectionManager
) {
    /**
     * Creates a [ConfigWriter].
     *
     * @param messagingConfig Config to be used by the subscription.
     *
     * @throws `CordaMessageAPIException` If the publisher cannot be set up.
     */
    internal fun create(messagingConfig: SmartConfig): ConfigWriter {
        val publisher = createPublisher(messagingConfig)
        val validator = configValidatorFactory.createConfigValidator()
        val subscription = createRPCSubscription(messagingConfig, publisher, validator)
        return ConfigWriter(subscription, publisher)
    }

    /**
     * Creates a [Publisher] using the provided [config].
     *
     * @throws `CordaMessageAPIException` If the publisher cannot be set up.
     */
    private fun createPublisher(config: SmartConfig): Publisher {
        val publisherConfig = PublisherConfig(CLIENT_NAME_DB)
        return publisherFactory.createPublisher(publisherConfig, config)
    }

    /**
     * Creates a [ConfigurationManagementRPCSubscription] using the provided [messagingConfig].
     * @param messagingConfig messaging config to create the subscription
     * @param publisher Used to write config to the topic
     * @param validator New configs received will be validated by the config [validator] with defaults applied.
     * @return RPC subscription for the [CONFIG_MGMT_REQUEST_TOPIC] topic, and handles requests using a [ConfigWriterProcessor].
     */
    private fun createRPCSubscription(
        messagingConfig: SmartConfig,
        publisher: Publisher,
        validator: ConfigurationValidator
    ): ConfigurationManagementRPCSubscription {

        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            CONFIG_MGMT_REQUEST_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java,
        )
        val configEntityWriter = ConfigEntityWriter(dbConnectionManager)
        val processor = ConfigWriterProcessor(publisher, configEntityWriter, validator)

        return subscriptionFactory.createRPCSubscription(rpcConfig, messagingConfig, processor)
    }
}
