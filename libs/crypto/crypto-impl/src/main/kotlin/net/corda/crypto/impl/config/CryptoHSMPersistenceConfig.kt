package net.corda.crypto.impl.config

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.crypto.exceptions.CryptoConfigurationException

/**
 * The HSM service persistence configuration.
 */
class CryptoHSMPersistenceConfig(internal val config: SmartConfig) : SmartConfig by config {
    val expireAfterAccessMins: Long
        get() = try {
            config.getLong(this::expireAfterAccessMins.name)
        } catch (e: Throwable) {
            throw CryptoConfigurationException("Failed to get ${this::expireAfterAccessMins.name}", e)
        }

    val maximumSize: Long
        get() = try {
            config.getLong(this::maximumSize.name)
        } catch (e: Throwable) {
            throw CryptoConfigurationException("Failed to get ${this::maximumSize.name}", e)
        }
}