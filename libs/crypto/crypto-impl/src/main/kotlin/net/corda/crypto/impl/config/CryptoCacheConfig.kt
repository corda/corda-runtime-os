package net.corda.crypto.impl.config

class CryptoCacheConfig(
    map: Map<String, Any?>
) : CryptoConfigMap(map) {
    companion object {
        val default = CryptoCacheConfig(emptyMap())
        const val DEFAULT_CACHE_FACTORY_NAME = "kafka"
    }

    val cacheFactoryName: String
        get() = getString(this::cacheFactoryName.name, DEFAULT_CACHE_FACTORY_NAME)

    val expireAfterAccessMins: Long
        get() = getLong(this::expireAfterAccessMins.name, 60)

    val maximumSize: Long
        get() = getLong(this::maximumSize.name, 100)

    val persistenceConfig: CryptoConfigMap
        get() = CryptoConfigMap(
            getOptionalConfig(this::persistenceConfig.name) ?: emptyMap()
        )
}