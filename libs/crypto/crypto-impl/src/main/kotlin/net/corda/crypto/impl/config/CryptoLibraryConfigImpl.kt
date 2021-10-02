package net.corda.crypto.impl.config

import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig.Companion.DEFAULT_MEMBER_KEY
import net.corda.v5.cipher.suite.config.CryptoMemberConfig

class CryptoLibraryConfigImpl(
    map: Map<String, Any?>
) : CryptoConfigMap(map), CryptoLibraryConfig {
    override fun getMember(memberId: String): CryptoMemberConfig {
        return CryptoMemberConfigImpl(
            getOptionalConfig(memberId) ?: getOptionalConfig(DEFAULT_MEMBER_KEY) ?: emptyMap()
        )
    }
}

val CryptoLibraryConfig.isDev: Boolean get() = CryptoConfigMap.getBoolean(this, this::isDev.name, false)

val CryptoLibraryConfig.keyCache: CryptoCacheConfig get() =
    CryptoCacheConfig(CryptoConfigMap.getOptionalConfig(this, this::keyCache.name) ?: emptyMap())

val CryptoLibraryConfig.mngCache: CryptoCacheConfig get() =
    CryptoCacheConfig(CryptoConfigMap.getOptionalConfig(this, this::mngCache.name) ?: emptyMap())

val CryptoLibraryConfig.cipherSuite: CipherSuiteConfig
    get() = CipherSuiteConfig(CryptoConfigMap.getOptionalConfig(this, this::cipherSuite.name) ?: emptyMap())

