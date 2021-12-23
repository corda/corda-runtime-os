package net.corda.messaging.utils

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.properties.ConfigProperties
import net.corda.messaging.properties.ConfigProperties.Companion.GROUP
import net.corda.messaging.properties.ConfigProperties.Companion.INSTANCE_ID
import net.corda.messaging.properties.ConfigProperties.Companion.TOPIC
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import java.net.URL
import java.util.*


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

/**
 * Read content of a [config] at a given [configPrefix] and its subsections as Java properties.
 * This is usable for generic configuration
 * of Kafka consumer/producers.
 * Override the values of the config with values from [overrideProperties].
 * @param config type safe config
 * @param configPrefix optional key prefix to read config from
 * @param overrideProperties properties to override at the given [configPrefix] in the [config].
 * @return properties with the same content as in config object, with prefix stripped.
 * Keys and values are strings with values overridden by overrideProperties
 */
fun mergeProperties(
    config: Config,
    configPrefix: String?,
    overrideProperties: Map<String, String>
): Properties {
    val properties = Properties()
    val configAtPrefix = if (configPrefix != null) {
        config.getConfig(configPrefix)
    } else {
        config
    }
    configAtPrefix.entrySet().forEach { (key) ->
        properties.setProperty(
            key,
            configAtPrefix.getString(key)
        )
    }
    properties.putAll(overrideProperties)
    return properties
}


fun Config.getStringOrNull(path: String) = if (hasPath(path)) getString(path) else null
fun Config.toProperties(): Properties = mergeProperties(this, null, emptyMap())
fun Config.render(): String =
    root().render(ConfigRenderOptions.defaults().setOriginComments(false).setComments(false).setJson(false))

fun Config.toPatternProperties(pattern: String, clientType: String? = null): String {
    val pathEnd = if (clientType != null) {
        "$pattern.$clientType"
    } else {
        pattern
    }
    return getConfig("messaging.pattern.$pathEnd")
        .toProperties().entries.sortedBy { it.key.toString() }.joinToString("\n")
}

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
