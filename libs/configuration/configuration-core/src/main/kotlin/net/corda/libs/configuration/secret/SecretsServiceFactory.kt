package net.corda.libs.configuration.secret

import com.typesafe.config.Config

interface SecretsServiceFactory {
    /**
     * Create and instance of [SecretsService] using the provided configuration
     * or return null if a service of this type cannot be created using the given configuration.
     *
     * @param config
     * @return implementation of [SecretsService] or null if none can be created of this type.
     */
    fun create(config: Config): SecretsService?
}

