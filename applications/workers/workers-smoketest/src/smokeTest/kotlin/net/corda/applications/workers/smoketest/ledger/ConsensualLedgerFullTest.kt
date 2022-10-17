package net.corda.applications.workers.smoketest.ledger

import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.conditionallyUploadCordaPackage
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.getOrCreateVirtualNodeFor
import net.corda.applications.workers.smoketest.registerMember
import net.corda.applications.workers.smoketest.startRpcFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS

/**
 * This file uses a dedicated CPI, Group Id and X500 names from ConsensualLedgerConfig.
 */
@Suppress("Unused", "FunctionName")
@TestInstance(PER_CLASS)
class ConsensualLedgerFullTest {

    private val aliceHoldingId: String = getHoldingIdShortHash(CONSENSUAL_X500_ALICE, CONSENSUAL_GROUP_ID)
    private val bobHoldingId: String = getHoldingIdShortHash(CONSENSUAL_X500_BOB, CONSENSUAL_GROUP_ID)

    @BeforeAll
    fun beforeAll() {
        // Upload test flows if not already uploaded
        conditionallyUploadCordaPackage(CONSENSUAL_TEST_CPI_NAME, CONSENSUAL_TEST_CPB_LOCATION,
            CONSENSUAL_GROUP_ID, CONSENSUAL_TEST_STATIC_MEMBER_LIST)

        // Make sure Virtual Nodes are created
        val aliceActualHoldingId = getOrCreateVirtualNodeFor(CONSENSUAL_X500_ALICE, CONSENSUAL_TEST_CPI_NAME)
        val bobActualHoldingId = getOrCreateVirtualNodeFor(CONSENSUAL_X500_BOB, CONSENSUAL_TEST_CPI_NAME)

        // Just validate the function and actual vnode holding ID hash are in sync
        // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
        assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)

        registerMember(aliceHoldingId)
        registerMember(bobHoldingId)
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
