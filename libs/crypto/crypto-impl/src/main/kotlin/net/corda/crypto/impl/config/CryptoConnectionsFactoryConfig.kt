package net.corda.crypto.impl.config

import net.corda.libs.configuration.SmartConfig

/**
 * The connections' factory configuration.
 *
 *  "cryptoConnectionFactory: {
 *      "connectionsExpireAfterAccessMins": 5,
 *      "connectionNumberLimit": 3
 *  }
 */
class CryptoConnectionsFactoryConfig(private val config: SmartConfig) : SmartConfig by config {
    val connectionsExpireAfterAccessMins: Long
        get() = try {
            config.getLong(this::connectionsExpireAfterAccessMins.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::connectionsExpireAfterAccessMins.name}", e)
        }

    val connectionNumberLimit: Long
        get() = try {
            config.getLong(this::connectionNumberLimit.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::connectionNumberLimit.name}", e)
        }
}