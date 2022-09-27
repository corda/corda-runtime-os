package net.corda.applications.workers.smoketest.ledger

import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.X500_CHARLIE
import net.corda.applications.workers.smoketest.X500_DAVID
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.conditionallyUploadCordaPackage
import net.corda.applications.workers.smoketest.flow.FlowTests
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
class ConsensualLedgerTests {

    companion object {
        var bobHoldingId: String = getHoldingIdShortHash(X500_BOB, GROUP_ID)
        var davidHoldingId: String = getHoldingIdShortHash(X500_DAVID, GROUP_ID)
        var charlieHoldingId: String = getHoldingIdShortHash(X500_CHARLIE, GROUP_ID)

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            // Upload test flows if not already uploaded
            conditionallyUploadCordaPackage(TEST_CPI_NAME, TEST_CPB_LOCATION, GROUP_ID)

            // Make sure Virtual Nodes are created
            val bobActualHoldingId = getOrCreateVirtualNodeFor(X500_BOB)
            val charlieActualHoldingId = getOrCreateVirtualNodeFor(X500_CHARLIE)
            val davidActualHoldingId = getOrCreateVirtualNodeFor(X500_DAVID)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(bobActualHoldingId).isEqualTo(FlowTests.bobHoldingId)
            assertThat(charlieActualHoldingId).isEqualTo(FlowTests.charlieHoldingId)
            assertThat(davidActualHoldingId).isEqualTo(FlowTests.davidHoldingId)

            registerMember(FlowTests.bobHoldingId)
            registerMember(FlowTests.charlieHoldingId)
        }
    }

    @Test
    fun `Consensual Ledger - Signed Transaction serialization and deserialization without exceptions`() {
        val requestID =
            startRpcFlow(
                bobHoldingId,
                mapOf(),
                "net.cordapp.testing.testflows.ledger.ConsensualSignedTransactionSerializationFlow"
            )
        val result = awaitRpcFlowFinished(bobHoldingId, requestID)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowError).isNull()
    }
}
