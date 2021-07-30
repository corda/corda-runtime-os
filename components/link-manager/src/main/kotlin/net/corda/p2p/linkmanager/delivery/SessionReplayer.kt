package net.corda.p2p.linkmanager.delivery

import net.corda.p2p.linkmanager.LinkManagerNetworkMap

interface SessionReplayer {

    data class SessionMessageReplay(
        val message: Any,
        val source: LinkManagerNetworkMap.HoldingIdentity,
        val dest: IdentityLookup
    )

    sealed class IdentityLookup {
        data class HoldingIdentity(val id: LinkManagerNetworkMap.HoldingIdentity): IdentityLookup()

        data class PublicKeyHash(val hash: ByteArray, val groupId: String): IdentityLookup() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as PublicKeyHash

                if (!hash.contentEquals(other.hash)) return false
                if (groupId != other.groupId) return false

                return true
            }

            override fun hashCode(): Int {
                var result = hash.contentHashCode()
                result = 31 * result + groupId.hashCode()
                return result
            }
        }
    }

    fun addMessageForReplay(uniqueId: String, messageReplay: SessionMessageReplay)

    fun removeMessageFromReplay(uniqueId: String)

}