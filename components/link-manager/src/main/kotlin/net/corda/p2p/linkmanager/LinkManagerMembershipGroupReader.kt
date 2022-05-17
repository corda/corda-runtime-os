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

    fun getMemberInfo(holdingIdentity: HoldingIdentity): MemberInfo?

    fun getMemberInfo(hash: ByteArray, groupId: String): MemberInfo?
}
