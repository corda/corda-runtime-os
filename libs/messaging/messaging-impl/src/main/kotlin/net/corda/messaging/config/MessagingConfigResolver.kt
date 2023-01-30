package net.corda.messaging.config

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.constants.SubscriptionType
import org.osgi.framework.FrameworkUtil
import org.slf4j.LoggerFactory

/**
 * Class to resolve configuration for the messaging layer.
 */
internal class MessagingConfigResolver(private val smartConfigFactory: SmartConfigFactory) {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        // Defaults could be provided externally using the schema instead. However, at the time of writing this isn't
        // integrated, so the default fallback mechanism is left as a compromise.
        private const val DEFAULT_CONFIG = "messaging-defaults.conf"
    }

    private val defaults = getResourceConfig(DEFAULT_CONFIG)

    /**
     * Merge the user configured values in [subscriptionConfig] with the [messagingConfig] and return a concrete class containing all
     * values used by the subscriptions. Include any defaults defined.
     * @param subscriptionType Type of subscription.
     * @param subscriptionConfig User configurable values for a subscription.
     * @param messagingConfig Messaging smart config.
     * @param counter Client counter.
     * @return concrete class containing all config values used by a subscription.
     */
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

    /**
     * Merge the user configured values in [publisherConfig] with the [messagingConfig] and return a concrete class containing all
     * values used by a publisher. Include any defaults defined.
     * @param publisherConfig User configurable values for a publisher.
     * @param messagingConfig Messaging smart config.
     * @return Concrete class containing all config values used by a publisher.
     */
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