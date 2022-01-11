package net.corda.libs.configuration.secret

import com.typesafe.config.Config

/**
 * Secrets lookup service interface
 * Looks up secrets on-demand
 */
interface SecretsLookupService {
    /**
     * Get secret for given [Config]
     *
     * @param key
     * @return
     */
    fun getValue(secretConfig: Config): String
}

