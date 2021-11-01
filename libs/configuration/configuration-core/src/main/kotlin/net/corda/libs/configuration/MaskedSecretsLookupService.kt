package net.corda.libs.configuration

import com.typesafe.config.ConfigValue

/**
 * Implementation of [SecretsLookupService] that never reveals a secret
 */
class MaskedSecretsLookupService : SecretsLookupService {
    override fun getValue(key: ConfigValue): String {
        return "*****"
    }
}