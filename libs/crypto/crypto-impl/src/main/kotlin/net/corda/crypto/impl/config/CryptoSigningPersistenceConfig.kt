package net.corda.crypto.impl.config

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.crypto.exceptions.CryptoConfigurationException

/**
 * The signing service persistence configuration.
 */
class CryptoSigningPersistenceConfig(internal val config: SmartConfig) : SmartConfig by config {
    val keysExpireAfterAccessMins: Long
        get() = try {
            config.getLong(this::keysExpireAfterAccessMins.name)
        } catch (e: Throwable) {
            throw CryptoConfigurationException("Failed to get ${this::keysExpireAfterAccessMins.name}", e)
        }

    val keyNumberLimit: Long
        get() = try {
            config.getLong(this::keyNumberLimit.name)
        } catch (e: Throwable) {
            throw CryptoConfigurationException("Failed to get ${this::keyNumberLimit.name}", e)
        }

    val vnodesExpireAfterAccessMins: Long
        get() = try {
            config.getLong(this::vnodesExpireAfterAccessMins.name)
        } catch (e: Throwable) {
            throw CryptoConfigurationException("Failed to get ${this::vnodesExpireAfterAccessMins.name}", e)
        }

    val vnodeNumberLimit: Long
        get() = try {
            config.getLong(this::vnodeNumberLimit.name)
        } catch (e: Throwable) {
            throw CryptoConfigurationException("Failed to get ${this::vnodeNumberLimit.name}", e)
        }

    val connectionsExpireAfterAccessMins: Long
        get() = try {
            config.getLong(this::connectionsExpireAfterAccessMins.name)
        } catch (e: Throwable) {
            throw CryptoConfigurationException("Failed to get ${this::connectionsExpireAfterAccessMins.name}", e)
        }

    val connectionNumberLimit: Long
        get() = try {
            config.getLong(this::connectionNumberLimit.name)
        } catch (e: Throwable) {
            throw CryptoConfigurationException("Failed to get ${this::connectionNumberLimit.name}", e)
        }
}