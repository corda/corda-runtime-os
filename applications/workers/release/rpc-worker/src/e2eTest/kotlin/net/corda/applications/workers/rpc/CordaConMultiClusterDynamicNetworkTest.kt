package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.utils.E2eClusterAConfig
import net.corda.applications.workers.rpc.utils.E2eClusterBConfig
import net.corda.applications.workers.rpc.utils.E2eClusterCConfig
import net.corda.applications.workers.rpc.utils.E2eClusterFactory
import net.corda.applications.workers.rpc.utils.E2eClusterMember
import net.corda.applications.workers.rpc.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rpc.utils.disableCLRChecks
import net.corda.applications.workers.rpc.utils.generateGroupPolicy
import net.corda.applications.workers.rpc.utils.onboardMembers
import net.corda.applications.workers.rpc.utils.onboardMgm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Three clusters are required for running this test. See `resources/RunNetworkTests.md` for more details.
 */
class CordaConMultiClusterDynamicNetworkTest {
    private val clusterA = E2eClusterFactory.getE2eCluster(E2eClusterCConfig).also { cluster ->
        cluster.addMembers(
            listOf(
                E2eClusterMember("O=Alice, L=London, C=GB"),
                E2eClusterMember("O=Bob, L=London, C=GB"),
                E2eClusterMember("O=Charlie, L=London, C=GB"),
                E2eClusterMember("O=Dustin, L=London, C=GB"),
                E2eClusterMember("O=Elaine, L=London, C=GB"),
                E2eClusterMember("O=Fred, L=London, C=GB"),
                E2eClusterMember("O=Ginny, L=London, C=GB"),
                E2eClusterMember("O=Harry, L=London, C=GB"),
                E2eClusterMember("O=Isabel, L=London, C=GB"),
                E2eClusterMember("O=Jimmy, L=London, C=GB"),
            )
        )
    }

    private val clusterB = E2eClusterFactory.getE2eCluster(E2eClusterBConfig).also { cluster ->
        cluster.addMembers(
            listOf(
                E2eClusterMember("O=Kate, L=London, C=GB"),
                E2eClusterMember("O=Loki, L=London, C=GB"),
                E2eClusterMember("O=Morgan, L=London, C=GB"),
                E2eClusterMember("O=Nick, L=London, C=GB"),
                E2eClusterMember("O=Olivia, L=London, C=GB"),
                E2eClusterMember("O=Peter, L=London, C=GB"),
                E2eClusterMember("O=Quinn, L=London, C=GB"),
                E2eClusterMember("O=Ronan, L=London, C=GB"),
                E2eClusterMember("O=Sally, L=London, C=GB"),
                E2eClusterMember("O=Thor, L=London, C=GB"),
            )
        )
    }

    private val clusterC = E2eClusterFactory.getE2eCluster(E2eClusterAConfig).also { cluster ->
        cluster.addMembers(
            listOf(
                E2eClusterMember("O=Una, L=London, C=GB"),
                E2eClusterMember("O=Victor, L=London, C=GB"),
                E2eClusterMember("O=Wanda, L=London, C=GB"),
                E2eClusterMember("O=Xavier, L=London, C=GB"),
                E2eClusterMember("O=Yelena, L=London, C=GB"),
                E2eClusterMember("O=Zack, L=London, C=GB")
            )
        )
    }

    private val mgmCluster = E2eClusterFactory.getE2eCluster(E2eClusterAConfig).also { cluster ->
        cluster.addMembers(
            listOf(E2eClusterMember("O=MGM, L=London, C=GB"))
        )
    }

    private val memberClusters = listOf(clusterA, clusterB, clusterC)

    @Test @Disabled("Enable this to onboard MGM")
    fun `Create mgm print group policy file`() {
        // SET CPI HASH FROM WHAT WAS MANUALLY UPLOADED - uploading same CPI to three different
        // clusters seems to result in same hash for all three
        val cpiHash = ""

        assertThat(cpiHash).isNotBlank
        onboardMgmVnode(cpiHash)
    }

    @Test @Disabled("Enable this to onboard Members")
    fun `onboard members`() {
        // SET CPI HASH FROM WHAT WAS MANUALLY UPLOADED - uploading same CPI to three different
        // clusters seems to result in same hash for all three
        val cpiHash = ""

        assertThat(cpiHash).isNotBlank
        onboardMembers(cpiHash)
    }

    private fun onboardMgmVnode(cpiHash: String) {
        // For the purposes of this test, the MGM cluster is
        // expected to have only one MGM (in reality there can be more on a cluster).
        assertThat(mgmCluster.members).hasSize(1)

        val mgm = mgmCluster.members[0]

        mgmCluster.disableCLRChecks()
        mgmCluster.onboardMgm(mgm, cpiHash)

        val memberGroupPolicy = mgmCluster.generateGroupPolicy(mgm.holdingId)
        println(memberGroupPolicy)
    }

    private fun onboardMembers(cpiHash: String) {

        val mgm = mgmCluster.members[0]

        memberClusters.forEach { cordaCluster ->
            cordaCluster.disableCLRChecks()
            cordaCluster.onboardMembers(mgm, cpiHash = cpiHash)
        }

        // Assert all members can see each other in their member lists.
        val allMembers = memberClusters.flatMap { it.members } + mgm
        (memberClusters).forEach { cordaCluster ->
            cordaCluster.members.forEach {
                cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
            }
        }
    }
}
