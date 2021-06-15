package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.AvroHoldingIdentity
import java.security.PublicKey

/**
 * This interface defines the parts of the Network Map required by the LinkManager.
 */
interface LinkManagerNetworkMap {

    companion object {
        internal fun AvroHoldingIdentity.toSessionNetworkMapPeer(): NetMapHoldingIdentity {
            return NetMapHoldingIdentity(this.x500Name, this.groupId)
        }

        internal fun NetMapHoldingIdentity.toAvroHoldingIdentity(): AvroHoldingIdentity {
            return AvroHoldingIdentity(this.x500Name, this.groupId)
        }

    }

    /**
     * Returns the [PublicKey] belonging a specific [holdingIdentity]
     */
    fun getPublicKey(holdingIdentity: NetMapHoldingIdentity): PublicKey?

    /**
     * Returns the [PublicKey] in the NetworkMap with SHA-256 hash [hash].
     */
    fun getPublicKeyFromHash(hash: ByteArray): PublicKey

    /**
     * Returns the [NetMapHoldingIdentity] in the NetworkMap. Which public key SHA-256 hashes to [hash].
     * Returns [null] if there is no such key.
     */
    fun getPeerFromHash(hash: ByteArray): NetMapHoldingIdentity?

    fun getEndPoint(holdingIdentity: NetMapHoldingIdentity): EndPoint?

    /**
     * Returns our PublicKey belonging to [groupId]
     */
    fun getOurPublicKey(groupId: String?): PublicKey?

    /**
     * Returns our [NetMapHoldingIdentity] belonging to [groupId]
     */
    fun getOurHoldingIdentity(groupId: String?): NetMapHoldingIdentity?

    /**
     * SignData with our private-key belonging to [groupId]
     */
    fun signData(groupId: String?, data: ByteArray): ByteArray

    data class EndPoint(val sni: String, val address: String)

    data class NetMapHoldingIdentity(val x500Name: String, val groupId: String?)
}