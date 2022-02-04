package net.corda.messaging.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.schema.messaging.INSTANCE_ID
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.properties.ConfigProperties.Companion.CLIENT_ID_COUNTER
import net.corda.messaging.properties.ConfigProperties.Companion.GROUP
import net.corda.messaging.properties.ConfigProperties.Companion.TOPIC
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil

internal class ConfigResolver(private val smartConfigFactory: SmartConfigFactory) {

    private companion object {
        private val logger = contextLogger()

        private const val ENFORCED_CONFIG_FILE = "messaging-enforced.conf"
        private const val DEFAULT_CONFIG_FILE = "messaging-defaults.conf"
    }

    private val enforced = getResourceConfig(ENFORCED_CONFIG_FILE)
    private val defaults = getResourceConfig(DEFAULT_CONFIG_FILE)

    fun resolveSubscriptionConfig(
        subscriptionConfig: SubscriptionConfig,
        messagingConfig: SmartConfig,
        clientID: Int
    ): SmartConfig {
        val resolvedConfig = enforced
            .withFallback(subscriptionConfig.toSmartConfig(clientID))
            .withFallback(messagingConfig)
            .withFallback(defaults)
            .resolve()
        logger.info("Resolved subscription config: ${resolvedConfig.root().render()}")

        return resolvedConfig
    }

    fun resolvePublisherConfig(publisherConfig: PublisherConfig, messagingConfig: SmartConfig) : SmartConfig {
        val resolvedConfig = enforced
            .withFallback(publisherConfig.toSmartConfig())
            .withFallback(messagingConfig)
            .withFallback(defaults)
            .resolve()

        logger.info("Resolved publisher config: ${resolvedConfig.root().render()}")

        return resolvedConfig
    }

    /**
     * Retrieve a resource from this bundle and convert it to a SmartConfig object.
     *
     * If this is running outside OSGi (e.g. a unit test) then fall back to standard Java classloader mechanisms.
     */
    private fun getResourceConfig(resource: String): SmartConfig {
        val bundle: Bundle? = FrameworkUtil.getBundle(this::class.java)
        val url = bundle?.getResource(resource)
            ?: this::class.java.classLoader.getResource(resource) ?: throw CordaMessageAPIConfigException("foo") // TODO
        val config = ConfigFactory.parseURL(url)
        return smartConfigFactory.create(config)
    }

    private fun SubscriptionConfig.toSmartConfig(clientID: Int): SmartConfig {
        val config = ConfigFactory.empty()
            .withValue(GROUP, ConfigValueFactory.fromAnyRef(groupName))
            .withValue(TOPIC, ConfigValueFactory.fromAnyRef(eventTopic))
            .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(instanceId))
            .withValue(CLIENT_ID_COUNTER, ConfigValueFactory.fromAnyRef(clientID))
        return smartConfigFactory.create(config)
    }

    private fun PublisherConfig.toSmartConfig() : SmartConfig {
        var config = ConfigFactory.empty()
            .withValue(GROUP, ConfigValueFactory.fromAnyRef(clientId))
        if (instanceId != null) {
            config = config.withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(instanceId))
        }
        return smartConfigFactory.create(config)
    }
}