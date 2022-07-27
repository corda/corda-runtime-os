package net.corda.crypto.config.impl

import net.corda.libs.configuration.SmartConfig

/**
 * The connections' factory configuration.
 */
class CryptoConnectionsFactoryConfig(private val config: SmartConfig) {
    val expireAfterAccessMins: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getLong(this::expireAfterAccessMins.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::expireAfterAccessMins.name}", e)
        }
    }

    val maximumSize: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getLong(this::maximumSize.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::maximumSize.name}", e)
        }
    }
}