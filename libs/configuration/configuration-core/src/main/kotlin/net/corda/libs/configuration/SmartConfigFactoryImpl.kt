package net.corda.libs.configuration

import com.typesafe.config.Config
import org.osgi.service.component.annotations.Component

/**
 * Temporary implementation of [SmartConfigFactory]
 * to be used until we have a working implementation of [SecretsLookupService]
 * Until then, no secrets will be resolved
 *
 * @constructor Create empty Smart config factory impl
 */
@Component(service = [SmartConfigFactory::class])
class SmartConfigFactoryImpl : SmartConfigFactory {
    companion object{
        private val maskedSecretsLookupService = MaskedSecretsLookupService()
    }

    override fun create(config: Config): SmartConfig {
        // TODO - add a real implementation of [SecretsLookupService]
        return SmartConfigImpl(config)
    }

    override fun createSafe(config: Config): SmartConfig {
        return SmartConfigImpl(config, maskedSecretsLookupService)
    }
}