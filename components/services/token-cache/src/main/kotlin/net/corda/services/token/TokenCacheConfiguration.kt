package net.corda.services.token

import net.corda.libs.configuration.SmartConfig

interface TokenCacheConfiguration {
    val tokenClaimTimeout: Int

    fun onConfigChange(config: Map<String, SmartConfig>)
}

