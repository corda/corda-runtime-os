package net.corda.crypto.config.impl

import net.corda.libs.configuration.SmartConfig

/**
 * The signing service configuration.
 */
class CryptoSigningServiceConfig(private val config: SmartConfig) {
    class CacheConfig(private val config: SmartConfig) {
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

    val cache: CacheConfig by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            CacheConfig(config.getConfig(this::cache.name))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::cache.name}", e)
        }
    }
}