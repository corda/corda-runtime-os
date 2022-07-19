package net.corda.crypto.impl.config

import net.corda.libs.configuration.SmartConfig

/**
 * The signing service configuration.
 */
class CryptoSigningServiceConfig(internal val config: SmartConfig) : SmartConfig by config {
    class Cache(internal val config: SmartConfig) : SmartConfig by config {
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
    }

    val cache: Cache
        get() = try {
            Cache(config.getConfig(this::cache.name))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::cache.name}", e)
        }
}