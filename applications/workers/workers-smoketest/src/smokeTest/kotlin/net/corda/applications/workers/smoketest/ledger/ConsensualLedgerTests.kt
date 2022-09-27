package net.corda.applications.workers.smoketest.ledger

import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.startRpcFlow
import org.assertj.core.api.Assertions.assertThat
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
        val bobHoldingId: String = getHoldingIdShortHash(X500_BOB, GROUP_ID)
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
