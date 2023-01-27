package net.corda.crypto.config.impl

import com.typesafe.config.Config

/**
 * The connections' factory configuration.
 */
class CryptoConnectionsFactoryConfig(private val config: Config) {
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