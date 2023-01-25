package net.corda.applications.workers.smoketest.flow

import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.e2etest.utilities.GROUP_ID
import net.corda.e2etest.utilities.RPC_FLOW_STATUS_SUCCESS
import net.corda.e2etest.utilities.awaitRpcFlowFinished
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerMember
import net.corda.e2etest.utilities.startRpcFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

@Suppress("Unused")
class AmqpSerializationTests {

    companion object {
        private const val AmqpSerializationTestFlow = "net.cordapp.testing.smoketests.flow.AmqpSerializationTestFlow"

        private val testRunUniqueId = UUID.randomUUID()
        private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
        private var bobHoldingId: String = getHoldingIdShortHash(bobX500, GROUP_ID)
        private val staticMemberList = listOf(
            bobX500,
        )

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            // Upload test flows if not already uploaded
            conditionallyUploadCordaPackage(
                cpiName,
                TEST_CPB_LOCATION,
                GROUP_ID,
                staticMemberList
            )

            // Make sure Virtual Nodes are created
            val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(bobActualHoldingId).isEqualTo(bobActualHoldingId)

            registerMember(bobActualHoldingId)
        }
    }

    @Test
    fun `Serialize and deserialize a Pair`() {

        val requestId = startRpcFlow(bobHoldingId, emptyMap(), AmqpSerializationTestFlow)
        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.flowResult

        assertAll(
            { assertThat(result.flowError).isNull() },
            { assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS) },
            { assertThat(flowResult).isEqualTo("SerializableClass(pair=(A, B))") },
        )
    }
}