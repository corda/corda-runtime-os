package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.utils.E2eClusterAConfig
import net.corda.applications.workers.rpc.utils.E2eClusterBConfig
import net.corda.applications.workers.rpc.utils.E2eClusterCConfig
import net.corda.applications.workers.rpc.utils.E2eClusterFactory
import net.corda.applications.workers.rpc.utils.E2eClusterMember
import net.corda.applications.workers.rpc.utils.allowClientCertificates
import net.corda.applications.workers.rpc.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rpc.utils.sslConfiguration
import net.corda.applications.workers.rpc.utils.generateGroupPolicy
import net.corda.applications.workers.rpc.utils.getGroupId
import net.corda.applications.workers.rpc.utils.onboardMembers
import net.corda.applications.workers.rpc.utils.onboardMgm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
    fun `Create mgm and allow members to join the group - one way TLS`() {
        onboardMultiClusterGroup(false)
    }

    @Test
    @Disabled("Disable mutual TLS test as both TLS modes can't run at the same time on the same cluster")
    fun `Create mgm and allow members to join the group - mutual TLS`() {
        onboardMultiClusterGroup(true)
    }

    /**
     * Onboard group and return group ID.
     */
    private fun onboardMultiClusterGroup(mutualTls: Boolean): String {
        val mgm = clusterC.members[0]

        clusterC.sslConfiguration(mutualTls)
        clusterC.onboardMgm(mgm, tempDir, mutualTls = mutualTls)

        val memberGroupPolicy = clusterC.generateGroupPolicy(mgm.holdingId)

        memberClusters.forEach { cordaCluster ->
            cordaCluster.sslConfiguration(mutualTls)
            cordaCluster.onboardMembers(mgm, memberGroupPolicy, tempDir) { certificatePem ->
                if (mutualTls) {
                    clusterC.allowClientCertificates(certificatePem, mgm)
                }
            }
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
