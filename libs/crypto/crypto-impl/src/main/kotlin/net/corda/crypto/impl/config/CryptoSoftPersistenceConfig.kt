package net.corda.crypto.impl.config

import net.corda.libs.configuration.SmartConfig

/**
 * The Soft HSM persistence configuration.
 */
class CryptoSoftPersistenceConfig(internal val config: SmartConfig) : SmartConfig by config {
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

    val maxAttempts: Int
        get() = try {
            config.getInt(this::maxAttempts.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::maxAttempts.name}", e)
        }

    val attemptTimeoutMills: Long
        get() = try {
            config.getLong(this::attemptTimeoutMills.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::attemptTimeoutMills.name}", e)
        }

    val salt: String
        get() = try {
            config.getString(this::salt.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::salt.name}", e)
        }

    val passphrase: String
        get() = try {
            config.getString(this::passphrase.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::passphrase.name}", e)
        }
}