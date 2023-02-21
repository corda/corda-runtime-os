package net.corda.applications.workers.rest

import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.applications.workers.rest.utils.E2eClusterMember
import net.corda.applications.workers.rest.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rest.utils.assertP2pConnectivity
import net.corda.applications.workers.rest.utils.createStaticMemberGroupPolicyJson
import net.corda.applications.workers.rest.utils.getCa
import net.corda.applications.workers.rest.utils.onboardStaticMembers
import net.corda.data.identity.HoldingIdentity
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit

class StaticNetworkTest {
    @TempDir
    lateinit var tempDir: Path

    private val cordaCluster = E2eClusterFactory.getE2eCluster().also { cluster ->
        cluster.addMembers(
            (1..5).map {
                E2eClusterMember("C=GB, L=London, O=Member-${cluster.uniqueName}")
            }
        )
    }

    @Test
    fun `register members`() {
        onboardStaticGroup(tempDir)
    }

    /*
    This test is disabled until CORE-6079 is ready.
    When CORE-6079 is ready, please delete the `register members` test (as this one will cover that use case as well)
    To run it locally while disabled follow the instruction in resources/RunP2PTest.md:
     */
    @Test
    @Disabled("This test is disabled until CORE-6079 is ready")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun `create a static network, register members and exchange messages between them via p2p`() {
        val groupId = onboardStaticGroup(tempDir)

        assertP2pConnectivity(
            HoldingIdentity(cordaCluster.members[0].name, groupId),
            HoldingIdentity(cordaCluster.members[1].name, groupId),
            cordaCluster.kafkaTestToolkit
        )
    }

    private fun onboardStaticGroup(tempDir: Path): String {
        val groupId = UUID.randomUUID().toString()
        val groupPolicy = createStaticMemberGroupPolicyJson(
            getCa(),
            groupId,
            cordaCluster
        )

        cordaCluster.onboardStaticMembers(groupPolicy, tempDir)

        // Assert all members can see each other in their member lists
        val allMembers = cordaCluster.members
        allMembers.forEach {
            cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
        }
        return groupId
    }
}
