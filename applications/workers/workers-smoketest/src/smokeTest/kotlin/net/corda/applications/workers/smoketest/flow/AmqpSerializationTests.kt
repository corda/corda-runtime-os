package net.corda.applications.workers.smoketest.flow

import net.corda.applications.workers.smoketest.utils.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.utils.TEST_CPI_NAME
import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import net.corda.e2etest.utilities.RPC_FLOW_STATUS_SUCCESS
import net.corda.e2etest.utilities.awaitRestFlowResult
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.startRpcFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.util.UUID

@Suppress("Unused")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AmqpSerializationTests : ClusterReadiness by ClusterReadinessChecker() {

    companion object {
        private const val AmqpSerializationTestFlow = "com.r3.corda.testing.smoketests.flow.AmqpSerializationTestFlow"

        private val testRunUniqueId = UUID.randomUUID()
        private val groupId = UUID.randomUUID().toString()
        private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
        private var bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
        private val staticMemberList = listOf(
            bobX500,
        )
    }

    @BeforeAll
    internal fun beforeAll() {
        // check cluster is ready
        assertIsReady(Duration.ofMinutes(1), Duration.ofMillis(100))
        // Upload test flows if not already uploaded
        conditionallyUploadCordaPackage(
            cpiName,
            TEST_CPB_LOCATION,
            groupId,
            staticMemberList
        )

        // Make sure Virtual Nodes are created
        val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)

        // Just validate the function and actual vnode holding ID hash are in sync
        // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
        assertThat(bobActualHoldingId).isEqualTo(bobActualHoldingId)
    }

    @Test
    fun `Serialize and deserialize a Pair`() {

        val requestId = startRpcFlow(bobHoldingId, emptyMap(), AmqpSerializationTestFlow)
        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertAll(
            { assertThat(flowResult.flowError).isNull() },
            { assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS) },
            { assertThat(flowResult.json!!.textValue()).isEqualTo("SerializableClass(pair=(A, B))") },
        )
    }
}
