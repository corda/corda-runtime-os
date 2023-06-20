package net.corda.libs.configuration.helper

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.exception.CordaAPIConfigException
import net.corda.libs.configuration.getStringOrDefault
import net.corda.schema.configuration.BootConfig

/**
 * Utility for getting a  configuration from a given configuration map from the configuration read service.
 * @param configKey Config to retrieve
 * @return SmartConfig for the given key
 * @throws CordaAPIConfigException when no config is found for the given key
 */
fun Map<String, SmartConfig>.getConfig(configKey: String): SmartConfig {
    return this[configKey]
        ?: throw CordaAPIConfigException(
            "Could not get config. missing key: $configKey"
        )
}

fun SmartConfig.getInputTopic(consumerGroup: String, default: String) : String {
    return if (this.hasPath(BootConfig.BOOT_INPUT_TOPICS)) {
        this.getConfig(BootConfig.BOOT_INPUT_TOPICS).getStringOrDefault(consumerGroup, default)
    } else {
        default
    }
}
