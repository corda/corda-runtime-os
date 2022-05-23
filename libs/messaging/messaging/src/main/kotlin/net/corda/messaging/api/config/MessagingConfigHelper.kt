package net.corda.messaging.api.config

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.exception.CordaAPIConfigException

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