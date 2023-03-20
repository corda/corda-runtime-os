package net.corda.crypto.config.impl

import net.corda.libs.configuration.SmartConfig

/**
 * The connections' factory configuration.
 */
class CryptoConnectionsFactoryConfig(config: SmartConfig) {
    val expireAfterAccessMins: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getLong(EXPIRE_AFTER_ACCESS_MINS)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get $EXPIRE_AFTER_ACCESS_MINS", e)
        }
    }

    val maximumSize: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getLong(MAXIMUM_SIZE)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get $MAXIMUM_SIZE", e)
        }
    }
}