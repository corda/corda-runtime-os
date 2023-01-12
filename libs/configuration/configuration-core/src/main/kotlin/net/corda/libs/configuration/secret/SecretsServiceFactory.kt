package net.corda.libs.configuration.secret

import com.typesafe.config.Config

interface SecretsServiceFactory {
    /**
     * [type] will be used to select an implementation of [SecretsServiceFactory].
     * This means that this should be unique, but, as it will be used in configuration, it should also be
     * something that is descriptive and easy to pass in as configuration values.
     */
    val type: String
    /**
     * Create and instance of [SecretsService] using the provided configuration
     * or return null if a service of this type cannot be created using the given configuration.
     *
     * @param secretsServiceConfig
     * @return implementation of [SecretsService] or null if none can be created of this type.
     */
    fun create(secretsServiceConfig: Config): SecretsService
}

