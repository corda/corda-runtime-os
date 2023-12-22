package net.corda.applications.workers.smoketest.network

import net.corda.applications.workers.smoketest.utils.TEST_CPB_LOCATION
import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.containsExactlyInAnyOrderActiveMembers
import net.corda.e2etest.utilities.startDynamicGroup
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Isolated
import java.time.Duration
import java.util.UUID

@Isolated("As it onboards MGM")
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
        val group = DEFAULT_CLUSTER.startDynamicGroup(mgmX500).also {
            assertThat(it.groupPolicy).isNotEmpty.isNotBlank
        }

        val aliceInfo = group.onboardMember(DEFAULT_CLUSTER, TEST_CPB_LOCATION, testUniqueId.toString(), aliceX500)
        val bobInfo = group.onboardMember(DEFAULT_CLUSTER, TEST_CPB_LOCATION, testUniqueId.toString(), bobX500)
        val notaryInfo = group.onboardMember(DEFAULT_CLUSTER, TEST_CPB_LOCATION, testUniqueId.toString(), notaryX500)

        val allMembers = listOf(group.mgm, aliceInfo, bobInfo, notaryInfo)
        val allMemberX500Names = allMembers.map { MemberX500Name.parse(it.x500Name).toString() }
        allMembers.forEach { member ->
            DEFAULT_CLUSTER.containsExactlyInAnyOrderActiveMembers(member.holdingId, allMemberX500Names)
        }
    }

}
