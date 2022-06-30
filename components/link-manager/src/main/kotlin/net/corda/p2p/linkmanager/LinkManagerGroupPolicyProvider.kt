package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile

interface LinkManagerGroupPolicyProvider : LifecycleWithDominoTile {

    fun getGroupInfo(holdingIdentity: HoldingIdentity): GroupPolicyListener.GroupInfo?

    fun registerListener(groupPolicyListener: GroupPolicyListener)
}
