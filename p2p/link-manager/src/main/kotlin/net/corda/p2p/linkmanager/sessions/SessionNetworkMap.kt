package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.linkmanager.sessions.SessionNetworkMap.Companion.toSessionNetworkMapPeer
import java.security.PublicKey

interface SessionNetworkMap {

    companion object {
        internal fun net.corda.p2p.crypto.Peer.toSessionNetworkMapPeer(): Peer {
            return Peer(this.x500Name, this.groupId)
        }

        internal fun Peer.toAvroPeer(): net.corda.p2p.crypto.Peer {
            return net.corda.p2p.crypto.Peer(this.x500Name, this.groupId)
        }

    }

    fun getPublicKey(peer: Peer): PublicKey?

    fun getPublicKeyFromHash(hash: ByteArray): PublicKey

    fun getPeerFromHash(hash: ByteArray): Peer?

    fun getEndPoint(peer: Peer): EndPoint?

    fun getOurPublicKey(): PublicKey

    fun getOurPeer(): Peer

    fun signData(data: ByteArray): ByteArray

    data class EndPoint(val sni: String, val address: String)

    data class Peer(val x500Name: String, val groupId: String?)
}