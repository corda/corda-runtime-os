package net.corda.flow.application.services

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.annotations.DoNotImplement

interface FlowConfigService {

    /**
     * Retrieves the config that is associated with the provided [configKey] if available.
     * @throws [IllegalArgumentException] if the given config doesn't exist.
     */
    fun getConfig(configKey: String): SmartConfig
}
