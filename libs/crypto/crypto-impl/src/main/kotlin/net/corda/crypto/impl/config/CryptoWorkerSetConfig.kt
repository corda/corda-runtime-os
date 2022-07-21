package net.corda.crypto.impl.config

import net.corda.libs.configuration.SmartConfig

class CryptoWorkerSetConfig(private val config: SmartConfig) : SmartConfig by config {

    class RetryConfig(private val config: SmartConfig) : SmartConfig by config {
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
    }

    class HSMConfig(private val config: SmartConfig) : SmartConfig by config {
        val name: String
            get() = try {
                config.getString(this::name.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::name.name}", e)
            }

        val cfg: SmartConfig
            get() = try {
                config.getConfig(this::cfg.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::cfg.name}", e)
            }
    }

    val topicSuffix: String
        get() = try {
            config.getString(this::topicSuffix.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::topicSuffix.name}", e)
        }

    val retry: RetryConfig
        get() = try {
            RetryConfig(config.getConfig(this::retry.name))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::retry.name}", e)
        }

    val hsm: HSMConfig
        get() = try {
            HSMConfig(config.getConfig(this::hsm.name))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::hsm.name}", e)
        }
}