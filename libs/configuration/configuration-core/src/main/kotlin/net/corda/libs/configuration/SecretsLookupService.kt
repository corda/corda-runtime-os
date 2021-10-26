package net.corda.libs.configuration

import com.typesafe.config.ConfigValue

/**
 * Secrets lookup service interface
 * Looks up secrets on-demand
 */
interface SecretsLookupService {
    /**
     * Get secret for given [ConfigValue]
     *
     * @param key
     * @return
     */
    fun getValue(key: ConfigValue): String
}