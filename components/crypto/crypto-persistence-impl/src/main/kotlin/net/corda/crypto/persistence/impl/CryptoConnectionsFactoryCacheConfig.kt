package net.corda.crypto.persistence.impl

import net.corda.crypto.config.impl.cryptoConnectionFactory
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.libs.configuration.SmartConfig

internal data class CryptoConnectionsFactoryCacheConfig(
    val expireAfterAccessMins: Long,
    val maximumSize: Long
)

internal fun Map<String, SmartConfig>.toCryptoConnectionsFactoryCacheConfig(): CryptoConnectionsFactoryCacheConfig {
    val cryptoConfig = toCryptoConfig()
    val cryptoConnectionFactoryConfig = cryptoConfig.cryptoConnectionFactory()
    return CryptoConnectionsFactoryCacheConfig(
        cryptoConnectionFactoryConfig.expireAfterAccessMins,
        cryptoConnectionFactoryConfig.maximumSize
    )
}