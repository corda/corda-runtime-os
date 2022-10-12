package net.corda.applications.workers.smoketest.ledger

import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.X500_ALICE
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.conditionallyUploadCordaPackage
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.getOrCreateVirtualNodeFor
import net.corda.applications.workers.smoketest.registerMember
import net.corda.applications.workers.smoketest.startRpcFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.TestMethodOrder

@Suppress("Unused", "FunctionName")
@Order(25)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(Lifecycle.PER_CLASS)
class ConsensualLedgerFullTest {

    companion object {
        val aliceHoldingId: String = getHoldingIdShortHash(X500_ALICE, CONSENSUAL_GROUP_ID)
        val bobHoldingId: String = getHoldingIdShortHash(X500_BOB, CONSENSUAL_GROUP_ID)

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            // Upload test flows if not already uploaded
            conditionallyUploadCordaPackage(TEST_CONSENSUAL_FULL_CPI_NAME, TEST_CONSENSUAL_FULL_CPB_LOCATION, CONSENSUAL_GROUP_ID)

            // Make sure Virtual Nodes are created
            val aliceActualHoldingId = getOrCreateVirtualNodeFor(X500_ALICE, TEST_CONSENSUAL_FULL_CPI_NAME)
            val bobActualHoldingId = getOrCreateVirtualNodeFor(X500_BOB, TEST_CONSENSUAL_FULL_CPI_NAME)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
            assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)

            registerMember(aliceHoldingId)
            registerMember(bobHoldingId)
        }

    }

    @Test
    fun `Consensual Ledger - Demo app - full flow`() {
        val requestID =
            startRpcFlow(
                aliceHoldingId,
                mapOf(),
                "net.cordapp.demo.consensual.ConsensualDemoFlow"
            )
        val result = awaitRpcFlowFinished(aliceHoldingId, requestID)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowError).isNull()
    }
}
