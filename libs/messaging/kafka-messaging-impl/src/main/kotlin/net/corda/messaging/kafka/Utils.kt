package net.corda.messaging.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CLIENT_ID_COUNTER
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.GROUP
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.INSTANCE_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC
import org.osgi.framework.Bundle
import java.util.*

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

fun resolvePublisherConfiguration(
    bundle: Bundle,
    subscriptionConfiguration: Config,
    nodeConfig: Config,
    clientIdCounter: Int,
    pattern: String
): Config {
    val enforcedResource = bundle.getResource("messaging-enforced.conf")
    val defaultsResource = bundle.getResource("messaging-defaults.conf")
    val enforced = ConfigFactory.parseURL(enforcedResource)
    val defaults = ConfigFactory.parseURL(defaultsResource)

    val config = enforced
        .withFallback(subscriptionConfiguration)
        .withValue(CLIENT_ID_COUNTER, ConfigValueFactory.fromAnyRef(clientIdCounter))
        .withFallback(nodeConfig)
        .withFallback(defaults)
        .resolve()
        .getConfig(pattern)

    return if (!subscriptionConfiguration.hasPath(INSTANCE_ID)) {
        // No instance id - remove the transactional Id as we don't want to do transactions
        config.withoutPath(KafkaProperties.PRODUCER_TRANSACTIONAL_ID)
    } else {
        config
    }
}

fun resolveSubscriptionConfiguration(
    bundle: Bundle,
    subscriptionConfiguration: Config,
    nodeConfig: Config,
    clientIdCounter: Int,
    pattern: String
): Config {
    val enforcedResource = bundle.getResource("messaging-enforced.conf")
    val defaultsResource = bundle.getResource("messaging-defaults.conf")
    val enforced = ConfigFactory.parseURL(enforcedResource)
    val defaults = ConfigFactory.parseURL(defaultsResource)

    return enforced
        .withFallback(subscriptionConfiguration)
        .withValue(CLIENT_ID_COUNTER, ConfigValueFactory.fromAnyRef(clientIdCounter))
        .withFallback(nodeConfig)
        .withFallback(defaults)
        .resolve()
        .getConfig(pattern)
}
