package net.corda.p2p.linkmanager

import java.security.PublicKey

/**
 * This interface defines the parts of the Network Map required by the LinkManager.
 */
interface LinkManagerNetworkMap {

    companion object {
        internal fun net.corda.p2p.payload.HoldingIdentity.toHoldingIdentity(): HoldingIdentity {
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
     * Returns the [MemberInfo] belonging a specific [holdingIdentity]
     */
    fun getMemberInfo(holdingIdentity: HoldingIdentity): MemberInfo?

    /**
     * Returns the [MemberInfo] which has a public key with SHA-256 hash [hash].
     */
    fun getMemberInfoFromPublicKeyHash(hash: ByteArray): MemberInfo?

    /**
     * Returns the [NetworkType] our [holdingIdentity].
     */
    fun getNetworkType(holdingIdentity: HoldingIdentity): NetworkType?

    data class MemberInfo(val holdingIdentity: HoldingIdentity, val publicKey: PublicKey, val endPoint: EndPoint)

    data class EndPoint(val address: String)

    data class HoldingIdentity(val x500Name: String, val groupId: String)

    enum class NetworkType {
        CORDA_4, CORDA_5
    }
}