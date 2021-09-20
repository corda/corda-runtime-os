package net.corda.crypto.impl.config

import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig.Companion.DEFAULT_MEMBER_KEY
import net.corda.v5.cipher.suite.config.CryptoMemberConfig

class CryptoLibraryConfigImpl(
    map: Map<String, Any?>
) : CryptoConfigMap(map), CryptoLibraryConfig {
    override fun getMember(memberId: String): CryptoMemberConfig {
        return CryptoMemberConfigImpl(
            getOptionalConfig(memberId) ?: getConfig(DEFAULT_MEMBER_KEY)
        )
    }
}

val CryptoLibraryConfig.isDev: Boolean get() = CryptoConfigMap.getBoolean(this, this::isDev.name, false)

val CryptoLibraryConfig.rpc: CryptoRpcConfig get() = CryptoRpcConfig(CryptoConfigMap.getConfig(this, this::rpc.name))

val CryptoLibraryConfig.keyCache: CryptoCacheConfig get() = CryptoCacheConfig(CryptoConfigMap.getConfig(this, this::keyCache.name))

val CryptoLibraryConfig.mngCache: CryptoCacheConfig get() = CryptoCacheConfig(CryptoConfigMap.getConfig(this, this::mngCache.name))

val CryptoLibraryConfig.cipherSuite: CipherSuiteConfig
    get() = CipherSuiteConfig(CryptoConfigMap.getOptionalConfig(this, this::cipherSuite.name) ?: emptyMap())

