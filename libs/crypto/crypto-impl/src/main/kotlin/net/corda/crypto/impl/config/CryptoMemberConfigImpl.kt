package net.corda.crypto.impl.config

import net.corda.v5.cipher.suite.config.CryptoMemberConfig
import net.corda.v5.cipher.suite.config.CryptoMemberConfig.Companion.DEFAULT_CATEGORY_KEY
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import java.time.Duration

/**
 * Defines a member configuration.
 * It expects that there should be at least one entry with the "default" key the rest of the keys are
 * categories, e.g. LEDGER, FRESH_KEYS, TLS, etc.
 */
class CryptoMemberConfigImpl(
    map: Map<String, Any?>
) : CryptoConfigMap(map), CryptoMemberConfig {
    companion object {
        private fun getInstance(map: CryptoConfigMap): CryptoServiceConfig {
            return CryptoServiceConfig(
                serviceName = map.getString(CryptoServiceConfig::serviceName.name, CryptoServiceConfig.DEFAULT_SERVICE_NAME),
                timeout = Duration.ofSeconds(map.getLong(CryptoServiceConfig::timeout.name, 5)),
                defaultSignatureScheme = map.getString(CryptoServiceConfig::defaultSignatureScheme.name, ECDSA_SECP256R1_CODE_NAME),
                serviceConfig = map.getOptionalConfig(CryptoServiceConfig::serviceConfig.name) ?: emptyMap()
            )
        }
    }

    override val default: CryptoServiceConfig get() = getInstance(getConfig(DEFAULT_CATEGORY_KEY))

    override fun getCategory(category: String): CryptoServiceConfig {
        val raw = getOptionalConfig(category)
            ?: getOptionalConfig(DEFAULT_CATEGORY_KEY)
            ?: CryptoConfigMap(emptyMap())
        return getInstance(raw)
    }

}