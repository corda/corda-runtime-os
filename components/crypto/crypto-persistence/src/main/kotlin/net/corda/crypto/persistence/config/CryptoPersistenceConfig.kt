package net.corda.crypto.persistence.config

import net.corda.libs.configuration.SmartConfig

class CryptoPersistenceConfig(
    val config: SmartConfig
) {
    val expireAfterAccessMins: Long get() =
        if(config.hasPath(this::expireAfterAccessMins.name)) {
            config.getLong(this::expireAfterAccessMins.name)
        } else {
                60
        }

    val maximumSize: Long get() =
        if(config.hasPath(this::maximumSize.name)) {
            config.getLong(this::maximumSize.name)
        } else {
            100
        }
}