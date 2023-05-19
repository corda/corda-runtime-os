package net.corda.applications.workers.rest

import net.corda.applications.workers.rest.utils.E2eCluster
import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.applications.workers.rest.utils.E2eClusterMember
import net.corda.applications.workers.rest.utils.E2eClusterMemberRole
import net.corda.applications.workers.rest.utils.E2eClusterMemberRole.NOTARY
import net.corda.applications.workers.rest.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rest.utils.assertP2pConnectivity
import net.corda.applications.workers.rest.utils.generateGroupPolicy
import net.corda.applications.workers.rest.utils.getGroupId
import net.corda.applications.workers.rest.utils.getMemberName
import net.corda.applications.workers.rest.utils.onboardMembers
import net.corda.applications.workers.rest.utils.onboardMgm
import net.corda.data.identity.HoldingIdentity
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SingleClusterDynamicNetworkTest {
    @TempDir
    lateinit var tempDir: Path

    private val cordaCluster = E2eClusterFactory.getE2eCluster().apply {
        addMembers((1..2).map { createTestMember("Member$it") })
        addMember(createTestMember("Notary", NOTARY))
        addMembers((3..4).map { createTestMember("Member$it") })
    }

    private val mgm = cordaCluster.createTestMember("Mgm")
    
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
        cordaCluster.onboardMgm(mgm, tempDir)

        val memberGroupPolicy = cordaCluster.generateGroupPolicy(mgm.holdingId)

        cordaCluster.onboardMembers(mgm, memberGroupPolicy, tempDir)

        // Assert all members can see each other in their member lists
        val allMembers = cordaCluster.members + mgm
        allMembers.forEach {
            cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
        }
        return cordaCluster.getGroupId(mgm.holdingId)
    }

    private fun E2eCluster.createTestMember(
        namePrefix: String,
        role: E2eClusterMemberRole? = null
    ): E2eClusterMember {
        val memberName = getMemberName<SingleClusterDynamicNetworkTest>(namePrefix)
        return role?.let {
            E2eClusterMember(memberName, it)
        } ?: E2eClusterMember(memberName)
    }
}
