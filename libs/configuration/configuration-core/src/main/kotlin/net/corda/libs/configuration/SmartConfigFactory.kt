package net.corda.libs.configuration

import com.typesafe.config.Config

interface SmartConfigFactory {
    /**
     * Convert a regular [Config] object into a [SmartConfig] one that is able to resolve secrets
     * using the given implementation of [SecretsLookupService].
     *
     * @param config
     * @return
     */
    fun create(config: Config): SmartConfig

    fun makeSecret(plainText: String): SmartConfig
}

