package net.corda.applications.workers.packagingtest.contractverification

import net.corda.applications.workers.e2etestutils.utils.E2eClusterAConfig
import net.corda.applications.workers.e2etestutils.utils.E2eClusterBConfig
import net.corda.applications.workers.e2etestutils.utils.E2eClusterCConfig
import net.corda.applications.workers.e2etestutils.utils.E2eClusterFactory
import net.corda.applications.workers.e2etestutils.utils.E2eClusterMember
import net.corda.applications.workers.e2etestutils.utils.allowClientCertificates
import net.corda.applications.workers.e2etestutils.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.e2etestutils.utils.generateGroupPolicy
import net.corda.applications.workers.e2etestutils.utils.onboardMembers
import net.corda.applications.workers.e2etestutils.utils.onboardMgm
import net.corda.applications.workers.e2etestutils.utils.setSslConfiguration
import java.nio.file.Path

internal class MultiClusterHelper(private var tempDir: Path, mutualTls: Boolean) {

    private val clusterA = E2eClusterFactory.getE2eCluster(E2eClusterAConfig).also { cluster ->
        cluster.addMembers(
            listOf(E2eClusterMember("O=Alice, L=London, C=GB, OU=${cluster.uniqueName}"))
        )
    }

    private val clusterB = E2eClusterFactory.getE2eCluster(E2eClusterBConfig).also { cluster ->
        cluster.addMembers(
            listOf(
                E2eClusterMember("O=Bob, L=London, C=GB, OU=${cluster.uniqueName}"),
                // E2eClusterMember("O=Notary, L=London, C=GB, OU=${cluster.uniqueName}", true)
            )
        )
    }

    private val clusterC = E2eClusterFactory.getE2eCluster(E2eClusterCConfig).also { cluster ->
        cluster.addMembers(
            listOf(E2eClusterMember("O=Mgm, L=London, C=GB, OU=${cluster.uniqueName}"))
        )
    }

    private val memberClusters = listOf(clusterA, clusterB)

    val memberGroupPolicy: String

    init {
        val mgm = clusterC.members[0]

        clusterC.setSslConfiguration(mutualTls)
        clusterC.onboardMgm(mgm, tempDir, mutualTls = mutualTls)

        memberGroupPolicy = clusterC.generateGroupPolicy(mgm.holdingId)

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
    }
}
