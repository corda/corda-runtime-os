package net.corda.services.token

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface TokenCacheConfigurationHandler: Lifecycle {
    fun onConfigChange(config: Map<String, SmartConfig>)
}