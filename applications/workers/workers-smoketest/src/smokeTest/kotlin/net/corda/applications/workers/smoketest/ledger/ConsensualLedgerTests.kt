package net.corda.applications.workers.smoketest.ledger

import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.conditionallyUploadCordaPackage
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.getOrCreateVirtualNodeFor
import net.corda.applications.workers.smoketest.registerMember
import net.corda.applications.workers.smoketest.startRpcFlow
import net.corda.applications.workers.smoketest.TEST_STATIC_MEMBER_LIST
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS

/**
 * This file uses the flow tests' CPI, Group Id and X500 names.
 */
@Suppress("Unused", "FunctionName")
@TestInstance(PER_CLASS)
@Disabled
class ConsensualLedgerTests {

    private val bobHoldingId: String = getHoldingIdShortHash(X500_BOB, GROUP_ID)

    @BeforeAll
    fun beforeAll() {
        // Upload test flows if not already uploaded
        conditionallyUploadCordaPackage(TEST_CPI_NAME, TEST_CPB_LOCATION, GROUP_ID, TEST_STATIC_MEMBER_LIST)

        // Make sure Virtual Nodes are created
        val bobActualHoldingId = getOrCreateVirtualNodeFor(X500_BOB)

        // Just validate the function and actual vnode holding ID hash are in sync
        // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)

        registerMember(bobHoldingId)
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
