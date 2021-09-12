package net.corda.components.crypto.config

import com.typesafe.config.Config

/**
 * Defines the crypto library configuration, the key in the members is the member id
 */
class CryptoLibraryConfig(private val raw: Config) {
    val rpc: CryptoRpcConfig get() = CryptoRpcConfig(raw.getConfig(this::rpc.name))

    val keyCache: CryptoCacheConfig get() = CryptoCacheConfig(raw.getConfig(this::keyCache.name))

    val mngCache: CryptoCacheConfig get() = CryptoCacheConfig(raw.getConfig(this::mngCache.name))

    fun getMember(id: String): CryptoMemberConfig = CryptoMemberConfig(raw.getConfig(id))
}
