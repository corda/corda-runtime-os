package net.corda.libs.configuration

import com.typesafe.config.Config
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.libs.configuration.secret.SecretsLookupService
import net.corda.libs.configuration.secret.SecretsService

class SmartConfigFactoryImpl(
    val secretsLookupService: SecretsLookupService,
    private val secretsCreateService: SecretsCreateService,
): SmartConfigFactory {
    constructor(secretsService: SecretsService) : this(secretsService, secretsService)
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

    override fun makeSecret(plainText: String): SmartConfig {
        return SmartConfigImpl(secretsCreateService.createValue(plainText), this, secretsLookupService)
    }
}