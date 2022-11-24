package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.utils.E2eClusterFactory
import net.corda.applications.workers.rpc.utils.E2eClusterMember
import net.corda.applications.workers.rpc.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rpc.utils.assertP2pConnectivity
import net.corda.applications.workers.rpc.utils.generateGroupPolicy
import net.corda.applications.workers.rpc.utils.getGroupId
import net.corda.applications.workers.rpc.utils.onboardMembers
import net.corda.applications.workers.rpc.utils.onboardMgm
import net.corda.data.identity.HoldingIdentity
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class SingleClusterDynamicNetworkTest {
    private val cordaCluster = E2eClusterFactory.getE2eCluster().also { cluster ->
        cluster.addMembers(
            (1..5).map {
                E2eClusterMember("C=GB, L=London, O=Member-${cluster.uniqueName}")
            }
        )
    }

    private val mgm = E2eClusterMember(
        "O=Mgm, L=London, C=GB, OU=${cordaCluster.uniqueName}"
    )

    @Test
    fun `Create mgm and allow members to join the group`() {
        onboardSingleClusterGroup()
    }

    /*
    This test is disabled until CORE-6079 is ready.
    When CORE-6079 is ready, please delete the `Create mgm and allow members to join the group` test
    (as this one will cover that use case as well)
    To run it locally while disabled follow the instruction in resources/RunP2PTest.md:
     */
    @Disabled("Is disabled and can be run manually until CORE-6079 is complete.")
    @Test
    fun `Onboard group and check p2p connectivity`() {
        val groupId = onboardSingleClusterGroup()

        assertP2pConnectivity(
            HoldingIdentity(cordaCluster.members[0].name, groupId),
            HoldingIdentity(cordaCluster.members[1].name, groupId),
            cordaCluster.kafkaTestToolkit
        )
    }

    /**
     * Onboard group and return group ID
     */
    private fun onboardSingleClusterGroup(): String {
        cordaCluster.onboardMgm(mgm)

        val memberGroupPolicy = cordaCluster.generateGroupPolicy(mgm.holdingId)

        cordaCluster.onboardMembers(mgm, memberGroupPolicy)

        // Assert all members can see each other in their member lists
        val allMembers = cordaCluster.members + mgm
        allMembers.forEach {
            cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
        }
        return cordaCluster.getGroupId(mgm.holdingId)
    }
}
