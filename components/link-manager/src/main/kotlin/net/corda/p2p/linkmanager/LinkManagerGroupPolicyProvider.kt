package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.virtualnode.HoldingIdentity

interface LinkManagerGroupPolicyProvider : LifecycleWithDominoTile {

    fun getGroupInfo(holdingIdentity: HoldingIdentity): GroupPolicyListener.GroupInfo?

    fun registerListener(groupPolicyListener: GroupPolicyListener)
}
