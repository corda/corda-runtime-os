package net.corda.services.token.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ServicesConfig
import net.corda.services.token.TokenCacheConfiguration

class TokenCacheConfigurationImpl : TokenCacheConfiguration {

    private var nullableConfig: SmartConfig? = null
    private val config: SmartConfig get() = checkNotNull(nullableConfig) { "The Configuration has not been set " }

    override val tokenClaimTimeout get() = config.getInt(ServicesConfig.TOKEN_CLAIM_TIMEOUT)

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        nullableConfig = checkNotNull(config.getConfig(ConfigKeys.SERVICES_CONFIG)) {
            "No '$ConfigKeys.SERVICES_CONFIG' section found in '${config}'"
        }
    }
}