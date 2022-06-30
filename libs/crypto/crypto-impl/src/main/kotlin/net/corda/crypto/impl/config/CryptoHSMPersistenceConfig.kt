package net.corda.crypto.impl.config

import net.corda.libs.configuration.SmartConfig

/**
 * The HSM service persistence configuration.
 */
class CryptoHSMPersistenceConfig(internal val config: SmartConfig) : SmartConfig by config {
    val expireAfterAccessMins: Long
        get() = try {
            config.getLong(this::expireAfterAccessMins.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::expireAfterAccessMins.name}", e)
        }

    val maximumSize: Long
        get() = try {
            config.getLong(this::maximumSize.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::maximumSize.name}", e)
        }

    val downstreamMaxAttempts: Int
        get() = try {
            config.getInt(this::downstreamMaxAttempts.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::downstreamMaxAttempts.name}", e)
        }
}