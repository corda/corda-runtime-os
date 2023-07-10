package net.corda.flow.application.services

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.annotations.DoNotImplement

@DoNotImplement
interface FlowConfigService {

    /**
     * Retrieves the config that is associated with the provided [configKey] if available.
     */
    fun getConfig(configKey: String): SmartConfig?
}
