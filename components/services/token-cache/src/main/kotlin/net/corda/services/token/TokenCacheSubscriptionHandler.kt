package net.corda.services.token

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface TokenCacheSubscriptionHandler: Lifecycle {
    fun onConfigChange(config: Map<String, SmartConfig>)
}