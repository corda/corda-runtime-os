package net.corda.crypto.component.persistence.config

import net.corda.crypto.impl.config.CryptoConfigMap

class CryptoPersistenceConfig(
    map: Map<String, Any?>
) : CryptoConfigMap(map) {
    val expireAfterAccessMins: Long
        get() = getLong(this::expireAfterAccessMins.name, 60)

    val maximumSize: Long
        get() = getLong(this::maximumSize.name, 100)
}