package net.corda.crypto.config.impl

import net.corda.libs.configuration.SmartConfig

class CryptoHSMConfig(private val config: SmartConfig) {

    class RetryConfig(private val config: SmartConfig) {
        val maxAttempts: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
            try {
                config.getInt(this::maxAttempts.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::maxAttempts.name}", e)
            }
        }

        val attemptTimeoutMills: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
            try {
                config.getLong(this::attemptTimeoutMills.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::attemptTimeoutMills.name}", e)
            }
        }
    }

    val masterKeyPolicy: MasterKeyPolicy by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getEnum(MasterKeyPolicy::class.java, this::masterKeyPolicy.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::masterKeyPolicy.name}", e)
        }
    }

    val cfg: SmartConfig by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getConfig(this::cfg.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::cfg.name}", e)
        }
    }

    val retry: RetryConfig by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            RetryConfig(config.getConfig(this::retry.name))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::retry.name}", e)
        }
    }
}