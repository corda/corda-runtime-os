package net.corda.applications.workers.rest

import net.corda.applications.workers.rest.utils.E2eCluster
import net.corda.applications.workers.rest.utils.E2eClusterAConfig
import net.corda.applications.workers.rest.utils.E2eClusterBConfig
import net.corda.applications.workers.rest.utils.E2eClusterCConfig
import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.applications.workers.rest.utils.E2eClusterMember
import net.corda.applications.workers.rest.utils.E2eClusterMemberRole
import net.corda.applications.workers.rest.utils.allowClientCertificates
import net.corda.applications.workers.rest.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rest.utils.generateGroupPolicy
import net.corda.applications.workers.rest.utils.getGroupId
import net.corda.applications.workers.rest.utils.getMemberName
import net.corda.applications.workers.rest.utils.onboardMembers
import net.corda.applications.workers.rest.utils.onboardMgm
import net.corda.applications.workers.rest.utils.setSslConfiguration
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

    private val clusterA = E2eClusterFactory.getE2eCluster(E2eClusterAConfig).apply {
        addMember(createTestMember("Alice"))
        addMember(createTestMember("Notary", E2eClusterMemberRole.NOTARY))
    }

    private val clusterB = E2eClusterFactory.getE2eCluster(E2eClusterBConfig).apply {
        addMember(createTestMember("Bob"))
        addMember(createTestMember("Charlie"))
    }

    private val clusterC = E2eClusterFactory.getE2eCluster(E2eClusterCConfig).apply {
        addMember(createTestMember("Mgm"))
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

        clusterC.setSslConfiguration(mutualTls)
        clusterC.onboardMgm(mgm, tempDir, mutualTls = mutualTls)

        val memberGroupPolicy = clusterC.generateGroupPolicy(mgm.holdingId)

        memberClusters.forEach { cordaCluster ->
            cordaCluster.setSslConfiguration(mutualTls)
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

    private fun E2eCluster.createTestMember(
        namePrefix: String,
        role: E2eClusterMemberRole? = null
    ): E2eClusterMember {
        val memberName = getMemberName<MultiClusterDynamicNetworkTest>(namePrefix)
        return role?.let {
            E2eClusterMember(memberName, it)
        } ?: E2eClusterMember(memberName)
    }
}
