package net.corda.applications.workers.smoketest.ledger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_FAILED
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.conditionallyUploadCordaPackage
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.getOrCreateVirtualNodeFor
import net.corda.applications.workers.smoketest.registerMember
import net.corda.applications.workers.smoketest.startRpcFlow
import net.corda.v5.crypto.SecureHash
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
class ConsensualLedgerTest {

    private companion object {
        const val CONSENSUAL_TEST_CPI_NAME = "ledger-consensual-demo-app"
        const val CONSENSUAL_TEST_CPB_LOCATION = "/META-INF/ledger-consensual-demo-app.cpb"
        const val CONSENSUAL_GROUP_ID = "7c5d6948-e17b-44e7-9d1c-fa4a3f667caf"

        const val CONSENSUAL_X500_ALICE = "CN=Alice, OU=Consensual, O=R3, L=London, C=GB"
        const val CONSENSUAL_X500_BOB = "CN=Bob, OU=Consensual, O=R3, L=London, C=GB"
        const val CONSENSUAL_X500_CHARLIE = "CN=Charlie, OU=Consensual, O=R3, L=London, C=GB"

        val CONSENSUAL_TEST_STATIC_MEMBER_LIST: List<String> = listOf(
            CONSENSUAL_X500_BOB,
            CONSENSUAL_X500_ALICE,
            CONSENSUAL_X500_CHARLIE
        )

        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
        }
    }

    private val aliceHoldingId: String = getHoldingIdShortHash(CONSENSUAL_X500_ALICE, CONSENSUAL_GROUP_ID)
    private val bobHoldingId: String = getHoldingIdShortHash(CONSENSUAL_X500_BOB, CONSENSUAL_GROUP_ID)
    private val charlieHoldingId: String = getHoldingIdShortHash(CONSENSUAL_X500_CHARLIE, CONSENSUAL_GROUP_ID)

    @BeforeAll
    fun beforeAll() {
        // Upload test flows if not already uploaded
        conditionallyUploadCordaPackage(
            CONSENSUAL_TEST_CPI_NAME,
            CONSENSUAL_TEST_CPB_LOCATION,
            CONSENSUAL_GROUP_ID,
            CONSENSUAL_TEST_STATIC_MEMBER_LIST
        )

        // Make sure Virtual Nodes are created
        val aliceActualHoldingId = getOrCreateVirtualNodeFor(CONSENSUAL_X500_ALICE, CONSENSUAL_TEST_CPI_NAME)
        val bobActualHoldingId = getOrCreateVirtualNodeFor(CONSENSUAL_X500_BOB, CONSENSUAL_TEST_CPI_NAME)
        val charlieActualHoldingId = getOrCreateVirtualNodeFor(CONSENSUAL_X500_CHARLIE, CONSENSUAL_TEST_CPI_NAME)

        // Just validate the function and actual vnode holding ID hash are in sync
        // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
        assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)

        registerMember(aliceHoldingId)
        registerMember(bobHoldingId)
        registerMember(charlieHoldingId)
    }

    @Test
    fun `Consensual Ledger - create a transaction containing states and finalize it`() {
        val input = "test input"
        val consensualFlowRequestId = startRpcFlow(
            aliceHoldingId,
            mapOf("input" to input, "members" to listOf(CONSENSUAL_X500_BOB, CONSENSUAL_X500_CHARLIE)),
            "net.cordapp.demo.consensual.ConsensualDemoFlow"
        )
        val consensualFlowResult = awaitRpcFlowFinished(aliceHoldingId, consensualFlowRequestId)
        assertThat(consensualFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(consensualFlowResult.flowError).isNull()

        for (holdingId in listOf(aliceHoldingId, bobHoldingId, charlieHoldingId)) {
            val findTransactionFlowRequestId = startRpcFlow(
                aliceHoldingId,
                mapOf("transactionId" to consensualFlowResult.flowResult!!),
                "net.cordapp.demo.consensual.LoadTransactionFlow"
            )
            val transactionResult = awaitRpcFlowFinished(aliceHoldingId, findTransactionFlowRequestId)
            assertThat(transactionResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            assertThat(transactionResult.flowError).isNull()

            val parsedResult = objectMapper
                .readValue(transactionResult.flowResult!!, FetchTransactionResponse::class.java)

            assertThat(parsedResult.transaction).isNotNull.withFailMessage {
                "Member with holding identity $holdingId did not receive the transaction"
            }
            assertThat(parsedResult.transaction!!.id.toString()).isEqualTo(consensualFlowResult.flowResult)
            assertThat(parsedResult.transaction.states.map { it.testField }).containsOnly(input)
            assertThat(parsedResult.transaction.states.flatMap { it.participants }).hasSize(3)
            assertThat(parsedResult.transaction.participants).hasSize(3)
        }
    }

    @Test
    fun `Consensual Ledger - creating a transaction that fails custom verification causes finality to fail`() {
        val consensualFlowRequestId = startRpcFlow(
            aliceHoldingId,
            mapOf("input" to "fail", "members" to listOf(CONSENSUAL_X500_BOB, CONSENSUAL_X500_CHARLIE)),
            "net.cordapp.demo.consensual.ConsensualDemoFlow"
        )
        val consensualFlowResult = awaitRpcFlowFinished(aliceHoldingId, consensualFlowRequestId)
        assertThat(consensualFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
        assertThat(consensualFlowResult.flowError?.message).contains("Transaction verification failed for transaction")
        assertThat(consensualFlowResult.flowError?.message).contains("when signature was requested")
    }

    data class TestConsensualStateResult(val testField: String, val participants: List<ByteArray>)

    data class ConsensualTransactionResult(
        val id: SecureHash,
        val states: List<TestConsensualStateResult>,
        val participants: List<ByteArray>
    )

    data class FetchTransactionResponse(
        val transaction: ConsensualTransactionResult?,
        val errorMessage: String?
    )
}
