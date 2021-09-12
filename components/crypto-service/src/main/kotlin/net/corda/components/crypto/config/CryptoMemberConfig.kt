package net.corda.components.crypto.config

import com.typesafe.config.Config
import net.corda.v5.cipher.suite.config.CryptoServiceConfig

/**
 * Defines a member configuration.
 * It expects that there should be at least one entry with the "default" key the rest of the keys are
 * categories, e.g. LEDGER, FRESH_KEYS, TLS, etc.
 */
class CryptoMemberConfig(private val raw: Config) {
    companion object {
        const val DEFAULT_KEY = "default"
    }

    val default: CryptoServiceConfig get() = getCategory(DEFAULT_KEY)

    fun getCategory(category: String): CryptoServiceConfig  {
        val raw = raw.getConfig(category)
        return CryptoServiceConfig(
            serviceName = raw.getString(CryptoServiceConfig::serviceName.name),
            timeout = raw.getDuration(CryptoServiceConfig::timeout.name),
            //defaultSignatureScheme = raw.getString(CryptoServiceConfig::defaultSignatureScheme.name), // TODO2
            serviceConfig = raw.getConfig(CryptoServiceConfig::serviceConfig.name).root().unwrapped()
        )
    }
}