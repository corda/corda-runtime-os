package net.corda.applications.workers.rest

import net.corda.applications.workers.rest.utils.E2eCluster
import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.applications.workers.rest.utils.E2eClusterMember
import net.corda.applications.workers.rest.utils.E2eClusterMemberRole
import net.corda.applications.workers.rest.utils.E2eClusterMemberRole.NOTARY
import net.corda.applications.workers.rest.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rest.utils.assertP2pConnectivity
import net.corda.applications.workers.rest.utils.createStaticMemberGroupPolicyJson
import net.corda.applications.workers.rest.utils.getCa
import net.corda.applications.workers.rest.utils.getMemberName
import net.corda.applications.workers.rest.utils.onboardStaticMembers
import net.corda.data.identity.HoldingIdentity
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit

@Disabled("CORE-13288: Tests are disabled until there is a solution in place")
class StaticNetworkTest {
    @TempDir
    lateinit var tempDir: Path

    private val cordaCluster = E2eClusterFactory.getE2eCluster().apply {
        addMembers((1..2).map { createTestMember("Member$it") })
        addMember(createTestMember("Notary", NOTARY))
        addMembers((3..4).map { createTestMember("Member$it") })
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

    private fun E2eCluster.createTestMember(
        namePrefix: String,
        role: E2eClusterMemberRole? = null
    ): E2eClusterMember {
        val memberName = getMemberName<StaticNetworkTest>(namePrefix)
        return role?.let {
            E2eClusterMember(memberName, it)
        } ?: E2eClusterMember(memberName)
    }
}
