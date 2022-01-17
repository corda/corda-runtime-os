package net.corda.libs.configuration

import com.typesafe.config.Config
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import net.corda.libs.configuration.secret.SecretsLookupService

class SmartConfigFactoryImpl(
    val secretsLookupService: SecretsLookupService
): SmartConfigFactory {
    /**
     * Convert a regular [Config] object into a [SmartConfig] one that is able to resolve secrets
     * using the given implementation of [SecretsLookupService].
     *
     * @param config
     * @return
     */
    override fun create(config: Config): SmartConfig {
        return SmartConfigImpl(config, this, secretsLookupService)
    }
}