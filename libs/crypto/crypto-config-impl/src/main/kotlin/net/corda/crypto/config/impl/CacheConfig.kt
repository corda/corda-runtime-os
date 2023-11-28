package net.corda.crypto.config.impl

import net.corda.libs.configuration.SmartConfig

class CacheConfig(config: SmartConfig) {
    val expireAfterAccessMins: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getConfig(this::expireAfterAccessMins.name).getLong(DEFAULT)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::expireAfterAccessMins.name}", e)
        }
    }

    val maximumSize: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getConfig(this::maximumSize.name).getLong(DEFAULT)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::maximumSize.name}", e)
        }
    }
}
