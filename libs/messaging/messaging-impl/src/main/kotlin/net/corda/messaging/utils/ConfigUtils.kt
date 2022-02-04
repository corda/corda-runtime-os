package net.corda.messaging.utils

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.messaging.INSTANCE_ID
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.properties.ConfigProperties
import net.corda.messaging.properties.ConfigProperties.Companion.GROUP
import net.corda.messaging.properties.ConfigProperties.Companion.TOPIC
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import java.net.URL


class ConfigUtils {

    companion object {
        private val enforced = ConfigFactory.parseURL(getResourceURL("messaging-enforced.conf"))
        private val defaults = ConfigFactory.parseURL(getResourceURL("messaging-defaults.conf"))

        fun resolvePublisherConfiguration(
            subscriptionConfiguration: Config,
            kafkaConfig: SmartConfig,
            clientIdCounter: Int,
            pattern: String
        ): Config {
            // turn enforced in to a SmartConfig like kafkaConfig first so everything is a "smart" config
            val config = kafkaConfig.convert(enforced)
                .withFallback(subscriptionConfiguration)
                .withValue(ConfigProperties.CLIENT_ID_COUNTER, ConfigValueFactory.fromAnyRef(clientIdCounter))
                .withFallback(kafkaConfig)
                .withFallback(defaults)
                .resolve()
                .getConfig(pattern)

            return if (!subscriptionConfiguration.hasPath(INSTANCE_ID)) {
                // No instance id - remove the transactional Id as we don't want to do transactions
                config.withoutPath(ConfigProperties.PRODUCER_TRANSACTIONAL_ID)
            } else {
                config
            }
        }

        fun resolveSubscriptionConfiguration(
            subscriptionConfiguration: Config,
            kafkaConfig: SmartConfig,
            clientIdCounter: Int,
            pattern: String
        ): Config {
            // turn enforced in to a SmartConfig like kafkaConfig first so everything is a "smart" config
            return  kafkaConfig.convert(enforced)
                .withFallback(subscriptionConfiguration)
                .withValue(ConfigProperties.CLIENT_ID_COUNTER, ConfigValueFactory.fromAnyRef(clientIdCounter))
                .withFallback(kafkaConfig)
                .withFallback(defaults)
                .resolve()
                .getConfig(pattern)
        }

        /**
         * Try get [resource] via osgi.
         * If that's null we're in a unit test so use the classes classloader.
         */
        private fun getResourceURL(resource: String) : URL? {
            val bundle: Bundle? = FrameworkUtil.getBundle(this::class.java)
            return bundle?.getResource(resource)
                ?: this::class.java.classLoader.getResource(resource)
        }
    }
}


fun Config.getStringOrNull(path: String) = if (hasPath(path)) getString(path) else null
fun Config.render(): String =
    root().render(ConfigRenderOptions.defaults().setOriginComments(false).setComments(false).setJson(false))

fun SubscriptionConfig.toConfig(): Config {
    return ConfigFactory.empty()
        .withValue(GROUP, ConfigValueFactory.fromAnyRef(groupName))
        .withValue(TOPIC, ConfigValueFactory.fromAnyRef(eventTopic))
        .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(instanceId))
}

fun PublisherConfig.toConfig(): Config {
    var config = ConfigFactory.empty()
        .withValue(GROUP, ConfigValueFactory.fromAnyRef(clientId))
    if (instanceId != null) {
        config = config.withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(instanceId))
    }
    return config
}
