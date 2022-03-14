package net.corda.messaging.config

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.constants.SubscriptionType
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil

internal class MessagingConfigResolver(private val smartConfigFactory: SmartConfigFactory) {

    private companion object {
        private val logger = contextLogger()

        // Defaults could be provided externally using the schema instead. However, at the time of writing this isn't
        // integrated, so the default fallback mechanism is left as a compromise.
        private const val DEFAULT_CONFIG = "messaging-defaults.conf"
    }

    private val defaults = getResourceConfig(DEFAULT_CONFIG)

    fun buildSubscriptionConfig(
        subscriptionType: SubscriptionType,
        subscriptionConfig: SubscriptionConfig,
        messagingConfig: SmartConfig,
        counter: Long
    ): ResolvedSubscriptionConfig {
        val config = messagingConfig.withFallback(defaults)
        return try {
            ResolvedSubscriptionConfig.merge(subscriptionType, subscriptionConfig, config, counter)
        } catch (e: ConfigException) {
            logger.error("Failed to resolve subscription config $subscriptionConfig: ${e.message}")
            throw CordaMessageAPIConfigException(
                "Failed to resolve subscription config $subscriptionConfig: ${e.message}",
                e
            )
        }
    }

    fun buildPublisherConfig(
        publisherConfig: PublisherConfig,
        messagingConfig: SmartConfig
    ): ResolvedPublisherConfig {
        val config = messagingConfig.withFallback(defaults)
        return try {
            ResolvedPublisherConfig.merge(publisherConfig, config)
        } catch (e: ConfigException) {
            logger.error("Failed to resolve publisher config $publisherConfig: ${e.message}")
            throw CordaMessageAPIConfigException("Failed to resolve publisher config $publisherConfig: ${e.message}", e)
        }
    }

    private fun getResourceConfig(resource: String): SmartConfig {
        val bundle = FrameworkUtil.getBundle(this::class.java)
        val url = bundle?.getResource(resource)
            ?: this::class.java.classLoader.getResource(resource)
            ?: throw CordaMessageAPIConfigException(
                "Failed to get resource $resource from Kafka bus implementation bundle"
            )
        val config = ConfigFactory.parseURL(url)
        return smartConfigFactory.create(config)
    }
}