package net.corda.applications.workers.smoketest.flow

import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.TEST_STATIC_MEMBER_LIST
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.conditionallyUploadCordaPackage
import net.corda.applications.workers.smoketest.getOrCreateVirtualNodeFor
import net.corda.applications.workers.smoketest.registerMember
import net.corda.applications.workers.smoketest.startRpcFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AmqpSerializationTests {

    companion object {
        private const val AmqpSerializationTestFlow = "net.cordapp.testing.smoketests.flow.AmqpSerializationTestFlow"

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            // Upload test flows if not already uploaded
            conditionallyUploadCordaPackage(TEST_CPI_NAME, TEST_CPB_LOCATION, GROUP_ID, TEST_STATIC_MEMBER_LIST)

            // Make sure Virtual Nodes are created
            val bobActualHoldingId = getOrCreateVirtualNodeFor(X500_BOB)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(bobActualHoldingId).isEqualTo(FlowTests.bobHoldingId)

            registerMember(FlowTests.bobHoldingId)
        }
    }

    @Test
    fun `Serialize and deserialize a Pair`() {

        val requestId = startRpcFlow(FlowTests.bobHoldingId, emptyMap(), AmqpSerializationTestFlow)
        val result = awaitRpcFlowFinished(FlowTests.bobHoldingId, requestId)

        val flowResult = result.flowResult

        assertAll(
            { assertThat(result.flowError).isNull() },
            { assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS) },
            { assertThat(flowResult).isEqualTo("SerializableClass(pair=(A, B))") },
        )
    }
}