package net.corda.p2p.linkmanager

import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import java.security.PublicKey

/**
 * This interface defines the parts of the Network Map required by the LinkManager.
 */
interface LinkManagerNetworkMap {

    companion object {
        internal fun net.corda.p2p.app.HoldingIdentity.toHoldingIdentity(): HoldingIdentity {
            return HoldingIdentity(x500Name, groupId)
        }

        internal fun net.corda.p2p.NetworkType.toNetworkType(): NetworkType? {
            return when (this) {
                net.corda.p2p.NetworkType.CORDA_4 -> NetworkType.CORDA_4
                net.corda.p2p.NetworkType.CORDA_5 -> NetworkType.CORDA_5
                else -> null
            }
        }

        internal fun NetworkType.toNetworkType(): net.corda.p2p.NetworkType {
            return when (this) {
                NetworkType.CORDA_4 -> net.corda.p2p.NetworkType.CORDA_4
                NetworkType.CORDA_5 -> net.corda.p2p.NetworkType.CORDA_5
            }
        }
    }

    /**
     * Returns the [MemberInfo] belonging a specific [holdingIdentity].
     */
    fun getMemberInfo(holdingIdentity: HoldingIdentity): MemberInfo?

    /**
     * Returns the [MemberInfo] which has a public key with SHA-256 hash [hash] and group identifier [groupId].
     */
    fun getMemberInfo(hash: ByteArray, groupId: String): MemberInfo?

    /**
     * Returns the [NetworkType] for group identifier [groupId].
     */
    fun getNetworkType(groupId: String): NetworkType?

    data class MemberInfo(val holdingIdentity: HoldingIdentity,
                          val publicKey: PublicKey,
                          val publicKeyAlgorithm: KeyAlgorithm,
                          val endPoint: EndPoint)

    data class EndPoint(val address: String)

    data class HoldingIdentity(val x500Name: String, val groupId: String) {
        fun toHoldingIdentity(): net.corda.p2p.app.HoldingIdentity {
            return net.corda.p2p.app.HoldingIdentity(x500Name, groupId)
        }
    }

    enum class NetworkType {
        CORDA_4, CORDA_5
    }
}