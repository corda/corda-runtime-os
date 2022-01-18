package net.corda.libs.configuration.secret

import com.typesafe.config.Config

/**
 * Implementation of [SecretsLookupService] that never reveals a secret
 */
class MaskedSecretsLookupService : SecretsLookupService {
    companion object {
        const val MASK_VALUE = "*****"
    }
    override fun getValue(secretConfig: Config): String {
        return MASK_VALUE
    }
}