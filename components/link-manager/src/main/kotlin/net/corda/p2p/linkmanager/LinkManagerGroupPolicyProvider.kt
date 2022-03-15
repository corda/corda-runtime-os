package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile

interface LinkManagerGroupPolicyProvider : LifecycleWithDominoTile {

    fun getGroupInfo(groupId: String): GroupPolicyListener.GroupInfo?

    fun registerListener(groupPolicyListener: GroupPolicyListener)
}
