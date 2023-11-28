package net.corda.crypto.config.impl

import net.corda.libs.configuration.SmartConfig

/**
 * The signing service configuration.
 */
class CryptoSigningServiceConfig(config: SmartConfig) {

    val cache: CacheConfig =
        try {
            CacheConfig(config.getConfig(CACHING))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get $CACHING", e)
        }
}
