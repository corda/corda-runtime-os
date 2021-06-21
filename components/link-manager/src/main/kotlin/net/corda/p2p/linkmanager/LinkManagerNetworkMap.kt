package net.corda.p2p.linkmanager

import java.security.PrivateKey
import java.security.PublicKey

/**
 * This interface defines the parts of the Network Map required by the LinkManager.
 */
interface LinkManagerNetworkMap {

    companion object {
        internal fun net.corda.p2p.HoldingIdentity.toSessionNetworkMapPeer(): HoldingIdentity {
            return HoldingIdentity(x500Name, groupId)
        }

        internal fun HoldingIdentity.toHoldingIdentity(): net.corda.p2p.HoldingIdentity {
            return net.corda.p2p.HoldingIdentity(this.x500Name, this.groupId)
        }

    }

    /**
     * Returns the [PublicKey] belonging a specific [holdingIdentity]
     */
    fun getPublicKey(holdingIdentity: HoldingIdentity): PublicKey?

    /**
     * Returns the [PublicKey] in the NetworkMap with SHA-256 hash [hash].
     */
    fun getPublicKeyFromHash(hash: ByteArray): PublicKey

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
     * Returns our [PrivateKey] belonging to [groupId]
     */
    fun getOurPrivateKey(groupId: String?): PrivateKey?


    /**
     * Returns our [HoldingIdentity] belonging to [groupId]
     */
    fun getOurHoldingIdentity(groupId: String?): HoldingIdentity?

    data class EndPoint(val sni: String, val address: String)

    data class HoldingIdentity(val x500Name: String, val groupId: String?)
}