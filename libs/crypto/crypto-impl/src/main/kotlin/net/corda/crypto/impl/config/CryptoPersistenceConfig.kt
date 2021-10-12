package net.corda.crypto.impl.config

class CryptoPersistenceConfig(
    map: Map<String, Any?>
) : CryptoConfigMap(map) {
    companion object {
        val default = CryptoPersistenceConfig(emptyMap())
        const val DEFAULT_FACTORY_NAME = "kafka"
    }

    val factoryName: String
        get() = getString(this::factoryName.name, DEFAULT_FACTORY_NAME)

    val expireAfterAccessMins: Long
        get() = getLong(this::expireAfterAccessMins.name, 60)

    val maximumSize: Long
        get() = getLong(this::maximumSize.name, 100)

    val persistenceConfig: CryptoConfigMap
        get() = CryptoConfigMap(
            getOptionalConfig(this::persistenceConfig.name) ?: emptyMap()
        )
}