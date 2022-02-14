package net.corda.messagebus

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
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


fun Config.toProperties(): Properties = mergeProperties(this, null, emptyMap())
fun Config.render(): String =
    root().render(ConfigRenderOptions.defaults().setOriginComments(false).setComments(false).setJson(false))

