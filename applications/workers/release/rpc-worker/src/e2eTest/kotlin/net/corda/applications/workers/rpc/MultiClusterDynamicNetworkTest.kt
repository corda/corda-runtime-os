package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.utils.E2eClusterAConfig
import net.corda.applications.workers.rpc.utils.E2eClusterBConfig
import net.corda.applications.workers.rpc.utils.E2eClusterCConfig
import net.corda.applications.workers.rpc.utils.E2eClusterFactory
import net.corda.applications.workers.rpc.utils.E2eClusterMember
import net.corda.applications.workers.rpc.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rpc.utils.disableCLRChecks
import net.corda.applications.workers.rpc.utils.generateGroupPolicy
import net.corda.applications.workers.rpc.utils.getGroupId
import net.corda.applications.workers.rpc.utils.onboardMembers
import net.corda.applications.workers.rpc.utils.onboardMgm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Three clusters are required for running this test. See `resources/RunNetworkTests.md` for more details.
 */
class MultiClusterDynamicNetworkTest {
    private val aliceCluster = E2eClusterFactory.getE2eCluster(E2eClusterAConfig).also { cluster ->
        cluster.addMembers(
            listOf(E2eClusterMember("O=Alice, L=London, C=GB, OU=${cluster.testToolkit.uniqueName}"))
        )
    }

    private val bobCluster = E2eClusterFactory.getE2eCluster(E2eClusterBConfig).also { cluster ->
        cluster.addMembers(
            listOf(E2eClusterMember("O=Bob, L=London, C=GB, OU=${cluster.testToolkit.uniqueName}"))
        )
    }

    private val mgmCluster = E2eClusterFactory.getE2eCluster(E2eClusterCConfig).also { cluster ->
        cluster.addMembers(
            listOf(E2eClusterMember("O=Mgm, L=London, C=GB, OU=${cluster.testToolkit.uniqueName}"))
        )
    }

    private val memberClusters = listOf(aliceCluster, bobCluster)

    @Test
    fun `Create mgm and allow members to join the group`() {
        onboardMultiClusterGroup()
    }

    /**
     * Onboard group and return group ID.
     */
    private fun onboardMultiClusterGroup(): String {
        // For the purposes of this test, the MGM cluster is
        // expected to have only one MGM (in reality there can be more on a cluster).
        assertThat(mgmCluster.members).hasSize(1)

        val mgm = mgmCluster.members[0]

        mgmCluster.disableCLRChecks()
        mgmCluster.onboardMgm(mgm)

        val memberGroupPolicy = mgmCluster.generateGroupPolicy(mgm.holdingId)

        memberClusters.forEach { cordaCluster ->
            println("${cordaCluster.clusterConfig.rpcHost}:${cordaCluster.clusterConfig.rpcPort}")
            cordaCluster.disableCLRChecks()
            cordaCluster.onboardMembers(mgm, memberGroupPolicy)
        }

        // Assert all members can see each other in their member lists.
        val allMembers = memberClusters.flatMap { it.members } + mgm
        (memberClusters + mgmCluster).forEach { cordaCluster ->
            cordaCluster.members.forEach {
                cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
            }
        }
        return mgmCluster.getGroupId(mgm.holdingId)
    }
}
