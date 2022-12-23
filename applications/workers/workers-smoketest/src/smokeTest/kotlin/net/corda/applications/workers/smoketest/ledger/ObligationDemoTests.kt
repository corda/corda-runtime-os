package net.corda.applications.workers.smoketest.ledger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.TEST_NOTARY_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_NOTARY_CPI_NAME
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
import java.math.BigDecimal
import java.util.UUID

@Suppress("Unused", "FunctionName")
@TestInstance(PER_CLASS)
class ObligationDemoTests {

    private companion object {
        const val TEST_CPI_NAME = "ledger-obligation-demo-app"
        const val TEST_CPB_LOCATION = "/META-INF/ledger-obligation-demo-app.cpb"

        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
        }
    }
    private val testRunUniqueId = UUID.randomUUID()
    private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
    private val notaryCpiName = "${TEST_NOTARY_CPI_NAME}_$testRunUniqueId"

    private val aliceX500 = "CN=Alice-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val notaryX500 = "CN=Notary-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"

    private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, GROUP_ID)
    private val bobHoldingId: String = getHoldingIdShortHash(bobX500, GROUP_ID)
    private val notaryHoldingId: String = getHoldingIdShortHash(notaryX500, GROUP_ID)

    private val staticMemberList = listOf(
        aliceX500,
        bobX500,
        notaryX500
    )

    @BeforeAll
    fun beforeAll() {
        conditionallyUploadCordaPackage(cpiName, TEST_CPB_LOCATION, GROUP_ID, staticMemberList)
        conditionallyUploadCordaPackage(notaryCpiName, TEST_NOTARY_CPB_LOCATION, GROUP_ID, staticMemberList)

        val aliceActualHoldingId = getOrCreateVirtualNodeFor(aliceX500, cpiName)
        val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)
        val notaryActualHoldingId = getOrCreateVirtualNodeFor(notaryX500, notaryCpiName)

        assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        assertThat(notaryActualHoldingId).isEqualTo(notaryHoldingId)

        registerMember(aliceHoldingId)
        registerMember(bobHoldingId)

        registerMember(notaryHoldingId, true)
    }

    @Test
    fun `Alice issues an obligation to Bob who then settles and deletes it`() {
        // 1. Alice issues Bob
        val createFlowRequestId = startRpcFlow(
            aliceHoldingId,
            mapOf(
                "issuer" to aliceX500,
                "holder" to bobX500,
                "amount" to 100,
                "notary" to notaryX500,
                "notaryService" to "O=MyNotaryService, L=London, C=GB"
            ),
            "net.cordapp.demo.obligation.workflow.CreateObligationFlow\$Initiator"
        )
        val createFlowResult = awaitRpcFlowFinished(aliceHoldingId, createFlowRequestId)
        assertThat(createFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(createFlowResult.flowError).isNull()

        val createResults = objectMapper
            .readValue(createFlowResult.flowResult, CreateObligationResult::class.java)

        // 2. Bob settles the obligation
        val updateFlowRequestId = startRpcFlow(
            bobHoldingId,
            mapOf(
                "id" to createResults.obligationId,
                "amountToSettle" to 100,
            ),
            "net.cordapp.demo.obligation.workflow.UpdateObligationFlow\$Initiator"
        )
        val updateFlowResult = awaitRpcFlowFinished(bobHoldingId, updateFlowRequestId)
        assertThat(updateFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(updateFlowResult.flowError).isNull()

        val updateResults = objectMapper
            .readValue(updateFlowResult.flowResult, UpdateObligationResult::class.java)

        // 3. Bob deletes the obligation
        val deleteFlowRequestId = startRpcFlow(
            bobHoldingId,
            mapOf(
                "id" to createResults.obligationId
            ),
            "net.cordapp.demo.obligation.workflow.DeleteObligationFlow\$Initiator"
        )
        val deleteFlowResult = awaitRpcFlowFinished(bobHoldingId, deleteFlowRequestId)
        assertThat(deleteFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(deleteFlowResult.flowError).isNull()

        val deleteResults = objectMapper
            .readValue(deleteFlowResult.flowResult, DeleteObligationResult::class.java)

        // 4. Verify if all the three transactions are in both Alice's and Bob's vaults.

        println(deleteFlowResult.flowResult!!)

        for (holdingId in listOf(aliceHoldingId, bobHoldingId)) {
            listOf(
                TestCase(createResults.transactionId, 2, 1),
                TestCase(updateResults.transactionId, 2, 1),
                TestCase(deleteResults.transactionId, 0, 2)
            ). forEach {
                println("Checking $holdingId $it.transactionId")
                val findTransactionFlowRequestId = startRpcFlow(
                    holdingId,
                    mapOf("transactionId" to it.transactionId.toString()),
                    "net.cordapp.demo.obligation.workflow.FindTransactionFlow"
                )
                val transactionResult = awaitRpcFlowFinished(holdingId, findTransactionFlowRequestId)
                assertThat(transactionResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
                assertThat(transactionResult.flowError).isNull()

                val parsedResult = objectMapper
                    .readValue(transactionResult.flowResult!!, FindTransactionResponse::class.java)

                assertThat(parsedResult.transaction).withFailMessage {
                    "Member with holding identity $holdingId did not receive the transaction $it.transactionId"
                }.isNotNull
                assertThat(parsedResult.transaction!!.id.toString()).isEqualTo(it.transactionId.toString())
                assertThat(parsedResult.transaction.states.flatMap { it.participants }).hasSize(it.expectedParticipantSize)
                assertThat(parsedResult.transaction.signatories).hasSize(it.expectedSignatoriesSize)
            }
        }
    }

    data class TestCase(val transactionId: SecureHash, val expectedParticipantSize: Int, val expectedSignatoriesSize: Int)

    data class CreateObligationResult(val transactionId: SecureHash, val obligationId: UUID)
    data class UpdateObligationResult(val transactionId: SecureHash)
    data class DeleteObligationResult(val transactionId: SecureHash)

    data class ObligationStateResult(val amount: BigDecimal, val id: UUID, val participants: List<ByteArray>)

    data class ObligationTransactionResult(
        val id: SecureHash,
        val states: List<ObligationStateResult>,
        val signatories: List<ByteArray>
    )

    data class FindTransactionResponse(
        val transaction: ObligationTransactionResult?,
        val errorMessage: String?
    )
}
