package net.corda.flow.application.services

import net.corda.libs.configuration.SmartConfig

interface FlowConfigService {

    /**
     * Retrieves the config that is associated with the provided [configKey] if available.
     * @throws [IllegalArgumentException] if the given config doesn't exist.
     */
    fun getConfig(configKey: String): SmartConfig
}
