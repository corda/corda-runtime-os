package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.AvroHoldingIdentity
import java.security.PublicKey

interface SessionNetworkMap {

    companion object {
        internal fun AvroHoldingIdentity.toSessionNetworkMapPeer(): NetMapHoldingIdentity {
            return NetMapHoldingIdentity(this.x500Name, this.groupId)
        }

        internal fun NetMapHoldingIdentity.toAvroHoldingIdentity(): AvroHoldingIdentity {
            return AvroHoldingIdentity(this.x500Name, this.groupId)
        }

    }

    fun getPublicKey(holdingIdentity: NetMapHoldingIdentity): PublicKey?

    fun getPublicKeyFromHash(hash: ByteArray): PublicKey

    fun getPeerFromHash(hash: ByteArray): NetMapHoldingIdentity?

    fun getEndPoint(holdingIdentity: NetMapHoldingIdentity): EndPoint?

    fun getOurPublicKey(groupId: String?): PublicKey?

    fun getOurHoldingIdentity(groupId: String?): NetMapHoldingIdentity?

    fun signData(groupId: String?, data: ByteArray): ByteArray

    data class EndPoint(val sni: String, val address: String)

    data class NetMapHoldingIdentity(val x500Name: String, val groupId: String?)
}