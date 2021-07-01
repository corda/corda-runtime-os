package net.corda.p2p.linkmanager

import net.corda.p2p.HoldingIdentity
import java.security.PublicKey

/**
 * This interface defines the parts of the Network Map required by the LinkManager.
 */
interface LinkManagerNetworkMap {

    companion object {
        internal fun net.corda.p2p.HoldingIdentity.toHoldingIdentity(): HoldingIdentity? {
            return when (this.identityType) {
                net.corda.p2p.IdentityType.CLASSIC_CORDA -> HoldingIdentity(x500Name, groupId, IdentityType.CLASSIC_CORDA)
                net.corda.p2p.IdentityType.CORDA_5 -> HoldingIdentity(x500Name, groupId, IdentityType.CORDA_5)
                null -> null
            }
        }

        internal fun HoldingIdentity.toHoldingIdentity(): net.corda.p2p.HoldingIdentity {
            return when (this.type) {
                IdentityType.CLASSIC_CORDA -> HoldingIdentity(this.x500Name, this.groupId, net.corda.p2p.IdentityType.CLASSIC_CORDA)
                IdentityType.CORDA_5 -> HoldingIdentity(this.x500Name, this.groupId, net.corda.p2p.IdentityType.CORDA_5)
            }
        }
    }

    /**
     * Return the hash of the [PublicKey]
     */
    fun hashPublicKey(publicKey: PublicKey): ByteArray

    /**
     * Returns the [PublicKey] belonging a specific [holdingIdentity]
     */
    fun getPublicKey(holdingIdentity: HoldingIdentity): PublicKey?

    /**
     * Returns the [PublicKey] in the NetworkMap [hash].
     * The hash algorithm should be the same as used in [LinkManagerNetworkMap.PublicKey.toHash]
     */
    fun getPublicKeyFromHash(hash: ByteArray): PublicKey?

    /**
     * Returns the [HoldingIdentity] in the NetworkMap. Which public key SHA-256 hashes to [hash].
     * Returns [null] if there is no such key.
     */
    fun getPeerFromHash(hash: ByteArray): HoldingIdentity?

    fun getEndPoint(holdingIdentity: HoldingIdentity): EndPoint?

    /**
     * Returns our [PublicKey] belonging to [groupId]
     */
    fun getOurPublicKey(groupId: String?): PublicKey?

    /**
     * Returns our [HoldingIdentity] belonging to [groupId]
     */
    fun getOurHoldingIdentity(groupId: String?): HoldingIdentity?

    data class EndPoint(val address: String)

    data class HoldingIdentity(val x500Name: String, val groupId: String, val type: IdentityType)

    enum class IdentityType {
        CLASSIC_CORDA, CORDA_5
    }
}