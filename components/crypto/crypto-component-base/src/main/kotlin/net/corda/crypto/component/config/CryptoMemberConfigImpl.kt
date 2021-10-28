package net.corda.crypto.component.config

import net.corda.crypto.impl.config.CryptoConfigMap
import net.corda.v5.cipher.suite.config.CryptoMemberConfig
import net.corda.v5.cipher.suite.config.CryptoMemberConfig.Companion.DEFAULT_CATEGORY_KEY
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import java.time.Duration

/**
 * Defines a member configuration for each key category.
 * It expects that there should be at least one entry with the "default" key the rest of the keys are
 * categories, e.g. LEDGER, FRESH_KEYS, TLS, etc.
 *
 * Example of the member's categories configuration:
 * mapOf(
 *  "default" to mapOf(
 *      "serviceName" to "default",
 *      "timeout" to "1",
 *      "defaultSignatureScheme" to "CORDA.ECDSA.SECP256K1",
 *      "serviceConfig" to mapOf(
 *          "passphrase" to "pwdD",
 *          "salt" to "saltD"
 *      )
 * ),
 * "LEDGER" to mapOf(
 *      "serviceName" to "FUTUREX",
 *      "timeout" to "10",
 *      "defaultSignatureScheme" to "CORDA.EDDSA.ED25519",
 *      "serviceConfig" to mapOf(
 *          "credentials" to "password"
 *      )
 *  )
 * )
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

    override val default: CryptoServiceConfig get() =
        getInstance(getOptionalConfig(DEFAULT_CATEGORY_KEY) ?: CryptoConfigMap(emptyMap()))

    override fun getCategory(category: String): CryptoServiceConfig {
        val raw = getOptionalConfig(category)
            ?: getOptionalConfig(DEFAULT_CATEGORY_KEY)
            ?: CryptoConfigMap(emptyMap())
        return getInstance(raw)
    }
}