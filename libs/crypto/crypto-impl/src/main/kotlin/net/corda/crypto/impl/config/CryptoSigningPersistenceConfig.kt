package net.corda.crypto.impl.config

import net.corda.libs.configuration.SmartConfig

/**
 * The signing service persistence configuration.
 */
class CryptoSigningPersistenceConfig(internal val config: SmartConfig) : SmartConfig by config {
    val keysExpireAfterAccessMins: Long
        get() = try {
            config.getLong(this::keysExpireAfterAccessMins.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::keysExpireAfterAccessMins.name}", e)
        }

    val keyNumberLimit: Long
        get() = try {
            config.getLong(this::keyNumberLimit.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::keyNumberLimit.name}", e)
        }

    val vnodesExpireAfterAccessMins: Long
        get() = try {
            config.getLong(this::vnodesExpireAfterAccessMins.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::vnodesExpireAfterAccessMins.name}", e)
        }

    val vnodeNumberLimit: Long
        get() = try {
            config.getLong(this::vnodeNumberLimit.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::vnodeNumberLimit.name}", e)
        }
}