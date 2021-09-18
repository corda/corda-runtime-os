package net.corda.crypto.impl.config

class CryptoCacheConfig(
    map: Map<String, Any?>
) : CryptoConfigMap(map) {
    val expireAfterAccessMins: Long
        get() = getLong(this::expireAfterAccessMins.name, 60)

    val maximumSize: Long
        get() = getLong(this::maximumSize.name, 100)

    val persistenceConfig: CryptoConfigMap
        get() = CryptoConfigMap(
            getOptionalConfig(this::persistenceConfig.name) ?: emptyMap()
        )
}