package net.corda.messaging.api.config

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.exception.CordaMessageAPIConfigException

/**
 * Utility for getting a  configuration from a given configuration map from the configuration read service.
 * @param configKey Config to retrieve
 * @return SmartConfig for the given key
 * @throws CordaMessageAPIConfigException when no config is found for the given key
 */
fun Map<String, SmartConfig>.getConfig(configKey: String): SmartConfig {
    return this[configKey]
        ?: throw CordaMessageAPIConfigException(
            "Could not generate a messaging patterns configuration due to missing key: $configKey"
        )
}