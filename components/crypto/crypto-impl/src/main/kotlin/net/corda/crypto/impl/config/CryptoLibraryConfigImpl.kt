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

fun CryptoLibraryConfig.getConfig(key: String): Map<String, Any?> =
    (this as CryptoConfigMap).getConfig(key)

fun CryptoLibraryConfig.getOptionalConfig(key: String): Map<String, Any?>? =
    (this as CryptoConfigMap).getOptionalConfig(key)

val CryptoLibraryConfig.rpc: CryptoRpcConfig get() = CryptoRpcConfig(getConfig(this::rpc.name))

val CryptoLibraryConfig.keyCache: CryptoCacheConfig get() = CryptoCacheConfig(getConfig(this::keyCache.name))

val CryptoLibraryConfig.mngCache: CryptoCacheConfig get() = CryptoCacheConfig(getConfig(this::mngCache.name))

val CryptoLibraryConfig.cipherSuite: CipherSuiteConfig
    get() = CipherSuiteConfig(getOptionalConfig(this::cipherSuite.name) ?: emptyMap())

