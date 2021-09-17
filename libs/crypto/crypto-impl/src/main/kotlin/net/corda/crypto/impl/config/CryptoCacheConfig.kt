package net.corda.crypto.impl.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class CryptoCacheConfig(private val raw: Config) {
    val expireAfterAccessMins: Long
        get() = if (raw.hasPath(this::expireAfterAccessMins.name)) {
            raw.getLong(this::expireAfterAccessMins.name)
        } else {
            60
        }
    val maximumSize: Long
        get() = if (raw.hasPath(this::maximumSize.name)) {
            raw.getLong(this::maximumSize.name)
        } else {
            100
        }
    val persistenceConfig: Config
        get() = if (raw.hasPath(this::persistenceConfig.name)) {
            raw.getConfig(this::persistenceConfig.name)
        } else {
            ConfigFactory.empty()
        }
}