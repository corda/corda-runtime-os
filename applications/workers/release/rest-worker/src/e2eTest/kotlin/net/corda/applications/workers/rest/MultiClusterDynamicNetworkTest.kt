package net.corda.applications.workers.rest

import net.corda.applications.workers.rest.utils.E2eClusterAConfig
import net.corda.applications.workers.rest.utils.E2eClusterBConfig
import net.corda.applications.workers.rest.utils.E2eClusterCConfig
import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.applications.workers.rest.utils.E2eClusterMember
import net.corda.applications.workers.rest.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rest.utils.disableGatewayCLRChecks
import net.corda.applications.workers.rest.utils.generateGroupPolicy
import net.corda.applications.workers.rest.utils.getGroupId
import net.corda.applications.workers.rest.utils.onboardMembers
import net.corda.applications.workers.rest.utils.onboardMgm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Three clusters are required for running this test. See `resources/RunNetworkTests.md` for more details.
 */
class MultiClusterDynamicNetworkTest {
    @TempDir
    lateinit var tempDir: Path

    private val clusterA = E2eClusterFactory.getE2eCluster(E2eClusterAConfig).also { cluster ->
        cluster.addMembers(
            listOf(E2eClusterMember("O=Alice, L=London, C=GB, OU=${cluster.uniqueName}"))
        )
    }

    private val clusterB = E2eClusterFactory.getE2eCluster(E2eClusterBConfig).also { cluster ->
        cluster.addMembers(
            listOf(E2eClusterMember("O=Bob, L=London, C=GB, OU=${cluster.uniqueName}"))
        )
    }

    private val clusterC = E2eClusterFactory.getE2eCluster(E2eClusterCConfig).also { cluster ->
        cluster.addMembers(
            listOf(E2eClusterMember("O=Mgm, L=London, C=GB, OU=${cluster.uniqueName}"))
        )
    }

    private val memberClusters = listOf(clusterA, clusterB)

    @BeforeEach
    fun validSetup() {
        // Verify that test clusters are actually configured with different endpoints.
        // If not, this test isn't testing what it should.
        assertThat(clusterA.clusterConfig.p2pHost)
            .isNotEqualTo(clusterB.clusterConfig.p2pHost)
            .isNotEqualTo(clusterC.clusterConfig.p2pHost)
        assertThat(clusterB.clusterConfig.p2pHost)
            .isNotEqualTo(clusterC.clusterConfig.p2pHost)

        // For the purposes of this test, the MGM cluster is
        // expected to have only one MGM (in reality there can be more on a cluster).
        assertThat(clusterC.members).hasSize(1)
    }

    @Test
    fun `Create mgm and allow members to join the group`() {
        onboardMultiClusterGroup()
    }

    /**
     * Onboard group and return group ID.
     */
    private fun onboardMultiClusterGroup(): String {
        val mgm = clusterC.members[0]

        clusterC.disableGatewayCLRChecks()
        clusterC.onboardMgm(mgm, tempDir)

        val memberGroupPolicy = clusterC.generateGroupPolicy(mgm.holdingId)

        memberClusters.forEach { cordaCluster ->
            cordaCluster.disableGatewayCLRChecks()
            cordaCluster.onboardMembers(mgm, memberGroupPolicy, tempDir)
        }

        // Assert all members can see each other in their member lists.
        val allMembers = memberClusters.flatMap { it.members } + mgm
        (memberClusters + clusterC).forEach { cordaCluster ->
            cordaCluster.members.forEach {
                cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
            }
        }
        return clusterC.getGroupId(mgm.holdingId)
    }
}
