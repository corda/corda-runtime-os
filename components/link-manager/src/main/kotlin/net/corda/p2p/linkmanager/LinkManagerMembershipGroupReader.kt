package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import java.security.PublicKey

interface LinkManagerMembershipGroupReader : LifecycleWithDominoTile {
    data class MemberInfo(
        val holdingIdentity: HoldingIdentity,
        val sessionPublicKey: PublicKey,
        val publicKeyAlgorithm: KeyAlgorithm,
        val endPoint: EndPoint,
    )

    /**
     * Lookup [lookupIdentity]'s MemberInfo inside [requestingIdentity]'s view of the Membership Group.
     */
    fun getMemberInfo(requestingIdentity: HoldingIdentity, lookupIdentity: HoldingIdentity): MemberInfo?

    /**
     * Lookup a MemberInfo by the SHA-256 of the session public key ([publicKeyHashToLookup]) inside
     * [requestingIdentity]'s view of the Membership Group.
     */
    fun getMemberInfo(requestingIdentity: HoldingIdentity, publicKeyHashToLookup: ByteArray): MemberInfo?
}
