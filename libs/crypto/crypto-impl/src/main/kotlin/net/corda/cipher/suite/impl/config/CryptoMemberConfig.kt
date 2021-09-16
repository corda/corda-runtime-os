package net.corda.cipher.suite.impl.config

import com.typesafe.config.Config
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import java.time.Duration

/**
 * Defines a member configuration.
 * It expects that there should be at least one entry with the "default" key the rest of the keys are
 * categories, e.g. LEDGER, FRESH_KEYS, TLS, etc.
 */
class CryptoMemberConfig(private val raw: Config) {
    companion object {
        const val DEFAULT_CATEGORY_KEY = "default"
    }

    val default: CryptoServiceConfig get() = getCategory(DEFAULT_CATEGORY_KEY)

    fun getCategory(category: String): CryptoServiceConfig {
        val raw = if (raw.hasPath(category)) {
            raw.getConfig(category)
        } else {
            raw.getConfig(DEFAULT_CATEGORY_KEY)
        }
        return CryptoServiceConfig(
            serviceName = if (raw.hasPath(CryptoServiceConfig::serviceName.name)) {
                raw.getString(CryptoServiceConfig::serviceName.name)
            } else {
                CryptoServiceConfig.DEFAULT_SERVICE_NAME
            },
            timeout = if (raw.hasPath(CryptoServiceConfig::timeout.name)) {
                Duration.ofSeconds(raw.getLong(CryptoServiceConfig::timeout.name))
            } else {
                Duration.ofSeconds(5)
            },
            defaultSignatureScheme = raw.getString(CryptoServiceConfig::defaultSignatureScheme.name),
            serviceConfig = if (raw.hasPath(CryptoServiceConfig::serviceConfig.name)) {
                raw.getConfig(CryptoServiceConfig::serviceConfig.name).root().unwrapped()
            } else {
                emptyMap()
            }
        )
    }
}