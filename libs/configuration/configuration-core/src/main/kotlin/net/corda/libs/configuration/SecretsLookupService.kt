package net.corda.libs.configuration

import com.typesafe.config.ConfigValue
import org.osgi.service.component.annotations.Component

/**
 * Secrets lookup service interface
 * Looks up secrets on-demand
 */
@Component(immediate = true, service = [SecretsLookupService::class])
interface SecretsLookupService {
    /**
     * Get secret for given [ConfigValue]
     *
     * @param key
     * @return
     */
    fun getValue(key: ConfigValue): String
}