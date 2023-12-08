package net.corda.applications.workers.smoketest.network

import net.corda.applications.workers.smoketest.utils.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.utils.TEST_CPI_NAME
import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.TEST_NOTARY_CPB_LOCATION
import net.corda.e2etest.utilities.TEST_NOTARY_CPI_NAME
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import net.corda.e2etest.utilities.containsExactlyInAnyOrderActiveMembers
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.Duration
import java.util.*

@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaticNetworkTest : ClusterReadiness by ClusterReadinessChecker() {

    private val testRunUniqueId = UUID.randomUUID()
    private val groupId = UUID.randomUUID().toString()

    private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
    private val notaryCpiName = "${TEST_NOTARY_CPI_NAME}_$testRunUniqueId"

    private val aliceX500 = "CN=Alice-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val notaryX500 = "CN=Notary-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"

    private val staticMemberList = listOf(
        aliceX500,
        bobX500,
        notaryX500
    )

    @BeforeAll
    fun setup() {
        // check cluster is ready
        assertIsReady(Duration.ofMinutes(1), Duration.ofMillis(100))
    }

    @Test
    fun `register members`() {
        DEFAULT_CLUSTER.conditionallyUploadCpiSigningCertificate()

        conditionallyUploadCordaPackage(
            cpiName,
            TEST_CPB_LOCATION,
            groupId,
            staticMemberList
        )
        conditionallyUploadCordaPackage(
            notaryCpiName,
            TEST_NOTARY_CPB_LOCATION,
            groupId,
            staticMemberList
        )

        val aliceHoldingId = getOrCreateVirtualNodeFor(aliceX500, cpiName)
        val bobHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)
        val notaryHoldingId = getOrCreateVirtualNodeFor(notaryX500, notaryCpiName)

        registerStaticMember(aliceHoldingId)
        registerStaticMember(bobHoldingId)
        registerStaticMember(notaryHoldingId, notaryServiceName = "O=TestNotaryService, L=London, C=GB")

        val allMembers = listOf(Pair(aliceHoldingId, aliceX500), Pair(bobHoldingId, bobX500), Pair(notaryHoldingId, notaryX500))
        allMembers.forEach { (memberHoldingId, _) ->
            DEFAULT_CLUSTER.containsExactlyInAnyOrderActiveMembers(memberHoldingId, allMembers.map { it.second })
        }
    }
}
