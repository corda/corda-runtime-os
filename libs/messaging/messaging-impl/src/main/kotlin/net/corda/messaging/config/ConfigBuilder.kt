package net.corda.messaging.config

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger

internal class ConfigBuilder(private val smartConfigFactory: SmartConfigFactory) {

    private companion object {
        private val logger = contextLogger()
    }

    fun buildSubscriptionConfig(
        subscriptionConfig: SubscriptionConfig,
        messagingConfig: SmartConfig,
        clientID: Int
    ): ResolvedSubscriptionConfig {
        // TODO: This is inserting some boot-like configuration properties into the messaging object, it should probably
        // be done elsewhere.
        messagingConfig.apply {
            withValue("boot.instanceId", ConfigValueFactory.fromAnyRef(subscriptionConfig.instanceId))
            withValue("boot.clientId", ConfigValueFactory.fromAnyRef(clientID))
        }
        return ResolvedSubscriptionConfig.merge(subscriptionConfig, messagingConfig)
    }

    fun buildPublisherConfig(
        publisherConfig: PublisherConfig,
        messagingConfig: SmartConfig
    ): ResolvedPublisherConfig {
        return ResolvedPublisherConfig.merge(publisherConfig, messagingConfig)
    }
}