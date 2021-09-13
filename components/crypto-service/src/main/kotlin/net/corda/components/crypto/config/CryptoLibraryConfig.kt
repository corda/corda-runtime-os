package net.corda.components.crypto.config

import com.typesafe.config.Config

/**
 * Defines the crypto library configuration, the key in the members is the member id
 */
class CryptoLibraryConfig(private val raw: Config) {
    companion object {
        const val DEFAULT_MEMBER_KEY = "default"
    }

    val rpc: CryptoRpcConfig get() = CryptoRpcConfig(raw.getConfig(this::rpc.name))

    val keyCache: CryptoCacheConfig get() = CryptoCacheConfig(raw.getConfig(this::keyCache.name))

    val mngCache: CryptoCacheConfig get() = CryptoCacheConfig(raw.getConfig(this::mngCache.name))

    fun getMember(memberId: String): CryptoMemberConfig =
        if (raw.hasPath(memberId)) {
            CryptoMemberConfig(raw.getConfig(memberId))
        } else {
            CryptoMemberConfig(raw.getConfig(DEFAULT_MEMBER_KEY))
        }
}
