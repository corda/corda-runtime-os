package net.corda.applications.workers.smoketest.network

import net.corda.applications.workers.smoketest.utils.TEST_CPB_LOCATION
import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.containsExactlyInAnyOrderActiveMembers
import net.corda.e2etest.utilities.exportGroupPolicy
import net.corda.e2etest.utilities.onboardMember
import net.corda.e2etest.utilities.onboardMgm
import net.corda.e2etest.utilities.onboardNotaryMember
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.Duration
import java.util.UUID

@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SingleClusterDynamicNetworkTest : ClusterReadiness by ClusterReadinessChecker() {
    private val testUniqueId = UUID.randomUUID()

    private val mgmX500 = "O=Mgm, L=London, C=GB, OU=${testUniqueId}"
    private val aliceX500 = "CN=Alice-${testUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val bobX500 = "CN=Bob-${testUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val notaryX500 = "CN=Notary-${testUniqueId}, OU=Application, O=R3, L=London, C=GB"

    @BeforeAll
    fun setup() {
        // check cluster is ready
        assertIsReady(Duration.ofMinutes(1), Duration.ofMillis(100))
    }

    @Test
    fun `Create mgm and allow members to join the group`() {
        val mgmInfo = DEFAULT_CLUSTER.onboardMgm(mgmX500)
        val groupPolicy = DEFAULT_CLUSTER.exportGroupPolicy(mgmInfo.holdingId).also {
            assertThat(it).isNotEmpty.isNotBlank
        }

        val aliceInfo = DEFAULT_CLUSTER.onboardMember(TEST_CPB_LOCATION, testUniqueId.toString(), groupPolicy, aliceX500)
        val bobInfo = DEFAULT_CLUSTER.onboardMember(TEST_CPB_LOCATION, testUniqueId.toString(), groupPolicy, bobX500)
        val notaryInfo = DEFAULT_CLUSTER.onboardNotaryMember(TEST_CPB_LOCATION, testUniqueId.toString(), groupPolicy, notaryX500)

        val allMembers = listOf(mgmInfo, aliceInfo, bobInfo, notaryInfo)
        val allMemberX500Names = allMembers.map { MemberX500Name.parse(it.x500Name).toString() }
        allMembers.forEach { member ->
            DEFAULT_CLUSTER.containsExactlyInAnyOrderActiveMembers(member.holdingId, allMemberX500Names)
        }
    }

}
