package net.corda.configuration.write.impl.writer

import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Config.CONFIG_MGMT_REQUEST_TOPIC

// Maybe inline in event handler
/** A factory for [ConfigurationManagementRPCSubscription]s. */
internal class RPCSubscriptionFactory(
    private val subscriptionFactory: SubscriptionFactory,
    private val configValidatorFactory: ConfigurationValidatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val configPublishService: ConfigPublishService
) {
    /**
     * Creates a [ConfigurationManagementRPCSubscription].
     *
     * @param messagingConfig Config to be used by the subscription.
     */
    internal fun create(messagingConfig: SmartConfig): ConfigurationManagementRPCSubscription {
        val validator = configValidatorFactory.createConfigValidator()
        return createRPCSubscription(messagingConfig, validator)
    }

    /**
     * Creates a [ConfigurationManagementRPCSubscription] using the provided [messagingConfig].
     * @param messagingConfig messaging config to create the subscription
     * @param validator New configs received will be validated by the config [validator] with defaults applied.
     * @return RPC subscription for the [CONFIG_MGMT_REQUEST_TOPIC] topic, and handles requests using a [ConfigWriterProcessor].
     */
    private fun createRPCSubscription(
        messagingConfig: SmartConfig,
        validator: ConfigurationValidator
    ): ConfigurationManagementRPCSubscription {

        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            CONFIG_MGMT_REQUEST_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java,
        )
        val configEntityWriter = ConfigEntityWriter(dbConnectionManager.getClusterEntityManagerFactory())
        val processor = ConfigWriterProcessor(configPublishService, configEntityWriter, validator)

        return subscriptionFactory.createRPCSubscription(rpcConfig, messagingConfig, processor)
    }
}
