package net.corda.crypto.config.impl

import com.typesafe.config.ConfigList
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

    val wrappingKeys: ConfigList by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getList(this::wrappingKeys.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::wrappingKeys.name}", e)
        }
    }

    val defaultWrappingKey: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getString(this::defaultWrappingKey.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::defaultWrappingKey.name}", e)
        }
    }

    val retrying: RetryConfig by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            RetryConfig(config.getConfig(this::retrying.name))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::retrying.name}", e)
        }
    }
}
