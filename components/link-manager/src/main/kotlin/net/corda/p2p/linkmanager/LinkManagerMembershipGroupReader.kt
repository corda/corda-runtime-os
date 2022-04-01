package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile

interface LinkManagerMembershipGroupReader : LifecycleWithDominoTile {

    fun getMemberInfo(holdingIdentity: LinkManagerInternalTypes.HoldingIdentity): LinkManagerInternalTypes.MemberInfo?

    fun getMemberInfo(hash: ByteArray, groupId: String): LinkManagerInternalTypes.MemberInfo?
}
