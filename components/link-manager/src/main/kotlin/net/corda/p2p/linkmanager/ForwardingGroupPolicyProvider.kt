package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider

internal class ForwardingGroupPolicyProvider(private val stubGroupPolicyProvider: StubGroupPolicyProvider,
                                             private val groupPolicyProvider: GroupPolicyProvider,
                                             private val thirdPartyComponentsMode: ThirdPartyComponentsMode): LinkManagerGroupPolicyProvider {


    override fun getGroupInfo(groupId: String): GroupPolicyListener.GroupInfo? {
        return if (thirdPartyComponentsMode == ThirdPartyComponentsMode.REAL) {
            val groupPolicy = groupPolicyProvider.getGroupPolicy()
            val groupInfo = GroupPolicyListener.GroupInfo(groupPolicy.groupId, )
        } else {

        }
    }

    override fun registerListener(groupPolicyListener: GroupPolicyListener) {
        TODO("Not yet implemented")
    }

    override val dominoTile: DominoTile
        get() = TODO("Not yet implemented")
}