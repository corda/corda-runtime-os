package net.corda.applications.workers.smoketest.ledger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.e2etest.utilities.GROUP_ID
import net.corda.e2etest.utilities.RPC_FLOW_STATUS_SUCCESS
import net.corda.e2etest.utilities.TEST_NOTARY_CPB_LOCATION
import net.corda.e2etest.utilities.TEST_NOTARY_CPI_NAME
import net.corda.e2etest.utilities.awaitRpcFlowFinished
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.e2etest.utilities.startRpcFlow
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.util.UUID

@Suppress("Unused", "FunctionName")
@TestInstance(PER_CLASS)
class UtxoLedgerTests {

    private companion object {
        const val TEST_CPI_NAME = "ledger-utxo-demo-app"
        const val TEST_CPB_LOCATION = "/META-INF/ledger-utxo-demo-app.cpb"

        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            val module = SimpleModule()
            module.addSerializer(SecureHash::class.java, SecureHashSerializer)
            module.addDeserializer(SecureHash::class.java, SecureHashDeserializer)
            registerModule(module)
        }
    }

    private val testRunUniqueId = UUID.randomUUID()
    private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
    private val notaryCpiName = "${TEST_NOTARY_CPI_NAME}_$testRunUniqueId"

    private val aliceX500 = "CN=Alice-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val charlieX500 = "CN=Charlie-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val notaryX500 = "CN=Notary-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"

    private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, GROUP_ID)
    private val bobHoldingId: String = getHoldingIdShortHash(bobX500, GROUP_ID)
    private val charlieHoldingId: String = getHoldingIdShortHash(charlieX500, GROUP_ID)
    private val notaryHoldingId: String = getHoldingIdShortHash(notaryX500, GROUP_ID)

    private val staticMemberList = listOf(
        aliceX500,
        bobX500,
        charlieX500,
        notaryX500
    )

    @BeforeAll
    fun beforeAll() {
        conditionallyUploadCordaPackage(
            cpiName,
            TEST_CPB_LOCATION,
            GROUP_ID,
            staticMemberList
        )
        conditionallyUploadCordaPackage(
            notaryCpiName,
            TEST_NOTARY_CPB_LOCATION,
            GROUP_ID,
            staticMemberList
        )

        val aliceActualHoldingId = getOrCreateVirtualNodeFor(aliceX500, cpiName)
        val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)
        val charlieActualHoldingId = getOrCreateVirtualNodeFor(charlieX500, cpiName)
        val notaryActualHoldingId = getOrCreateVirtualNodeFor(notaryX500, notaryCpiName)

        assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)
        assertThat(notaryActualHoldingId).isEqualTo(notaryHoldingId)

        registerStaticMember(aliceHoldingId)
        registerStaticMember(bobHoldingId)
        registerStaticMember(charlieHoldingId)

        registerStaticMember(notaryHoldingId, true)
    }

    @Test
    fun `Utxo Ledger - create a transaction containing states and finalize it then evolve it`() {
        val input = "test input"
        val utxoFlowRequestId = startRpcFlow(
            aliceHoldingId,
            mapOf("input" to input, "members" to listOf(bobX500, charlieX500), "notary" to notaryX500),
            "net.cordapp.demo.utxo.UtxoDemoFlow"
        )
        val utxoFlowResult = awaitRpcFlowFinished(aliceHoldingId, utxoFlowRequestId)
        assertThat(utxoFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(utxoFlowResult.flowError).isNull()

        for (holdingId in listOf(aliceHoldingId, bobHoldingId, charlieHoldingId)) {
            val findTransactionFlowRequestId = startRpcFlow(
                holdingId,
                mapOf("transactionId" to utxoFlowResult.flowResult!!),
                "net.cordapp.demo.utxo.FindTransactionFlow"
            )
            val transactionResult = awaitRpcFlowFinished(holdingId, findTransactionFlowRequestId)
            assertThat(transactionResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            assertThat(transactionResult.flowError).isNull()

            val parsedResult = objectMapper
                .readValue(transactionResult.flowResult!!, FindTransactionResponse::class.java)

            assertThat(parsedResult.transaction).withFailMessage {
                "Member with holding identity $holdingId did not receive the transaction ${utxoFlowResult.flowResult}"
            }.isNotNull
            assertThat(parsedResult.transaction!!.id.toString()).isEqualTo(utxoFlowResult.flowResult)
            assertThat(parsedResult.transaction.states.map { it.testField }).containsOnly(input)
            assertThat(parsedResult.transaction.states.flatMap { it.participants }).hasSize(3)
            assertThat(parsedResult.transaction.participants).hasSize(3)
        }

        val evolvedMessage = "evolved input"
        val evolveRequestId = startRpcFlow(
            bobHoldingId,
            mapOf("update" to evolvedMessage, "transactionId" to utxoFlowResult.flowResult!!, "index" to "0"),
            "net.cordapp.demo.utxo.UtxoDemoEvolveFlow"
        )
        val evolveFlowResult = awaitRpcFlowFinished(bobHoldingId, evolveRequestId)

        val parsedEvolveFlowResult = objectMapper
            .readValue(evolveFlowResult.flowResult!!, EvolveResponse::class.java)
        assertThat(parsedEvolveFlowResult.transactionId).isNotNull()
        assertThat(parsedEvolveFlowResult.errorMessage).isNull()
        assertThat(evolveFlowResult.flowError).isNull()

        // Peek into the last transaction

        val peekFlowId =  startRpcFlow(
            bobHoldingId,
            mapOf("transactionId" to parsedEvolveFlowResult.transactionId!!),
            "net.cordapp.demo.utxo.PeekTransactionFlow")

        val peekFlowResult = awaitRpcFlowFinished(bobHoldingId, peekFlowId)
        assertThat(peekFlowResult.flowError).isNull()
        assertThat(peekFlowResult.flowResult).isNotNull()

        val parsedPeekFlowResult = objectMapper
            .readValue(peekFlowResult.flowResult, PeekTransactionResponse::class.java)

        assertThat(parsedPeekFlowResult.errorMessage).isNull()
        assertThat(parsedPeekFlowResult.inputs).singleElement().extracting { it.testField }.isEqualTo(input)
        assertThat(parsedPeekFlowResult.outputs).singleElement().extracting { it.testField }.isEqualTo(evolvedMessage)
    }

    @Test
    fun `Utxo Ledger - create a transaction containing states and finalize it then consume it in a transactionbuilder sender flow`() {
        val input = "txbuilder test"
        val utxoFlowRequestId = startRpcFlow(
            aliceHoldingId,
            mapOf("input" to input, "members" to listOf(bobX500), "notary" to notaryX500),
            "net.cordapp.demo.utxo.UtxoDemoFlow"
        )
        val utxoFlowResult = awaitRpcFlowFinished(aliceHoldingId, utxoFlowRequestId)
        assertThat(utxoFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(utxoFlowResult.flowError).isNull()

        for (holdingId in listOf(aliceHoldingId, bobHoldingId)) {
            val findTransactionFlowRequestId = startRpcFlow(
                holdingId,
                mapOf("transactionId" to utxoFlowResult.flowResult!!),
                "net.cordapp.demo.utxo.FindTransactionFlow"
            )
            val transactionResult = awaitRpcFlowFinished(holdingId, findTransactionFlowRequestId)
            assertThat(transactionResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            assertThat(transactionResult.flowError).isNull()

            val parsedResult = objectMapper
                .readValue(transactionResult.flowResult!!, FindTransactionResponse::class.java)

            assertThat(parsedResult.transaction).withFailMessage {
                "Member with holding identity $holdingId did not receive the transaction ${utxoFlowResult.flowResult}"
            }.isNotNull
            assertThat(parsedResult.transaction!!.id.toString()).isEqualTo(utxoFlowResult.flowResult)
            assertThat(parsedResult.transaction.states.map { it.testField }).containsOnly(input)
            assertThat(parsedResult.transaction.states.flatMap { it.participants }).hasSize(2)
            assertThat(parsedResult.transaction.participants).hasSize(2)
        }

        val sendTxBuilder = startRpcFlow(
            charlieHoldingId,
            mapOf("member" to bobX500),
            "net.cordapp.demo.utxo.UtxoDemoTransactionBuilderSendingFlow"
        )
        val sendTxBuilderFlowResult = awaitRpcFlowFinished(charlieHoldingId, sendTxBuilder)

        assertThat(sendTxBuilderFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(sendTxBuilderFlowResult.flowError).isNull()

        // Peek into the last transaction

        val peekFlowId = startRpcFlow(
            charlieHoldingId,
            mapOf("transactionId" to sendTxBuilderFlowResult.flowResult!!),
            "net.cordapp.demo.utxo.PeekTransactionFlow")

        val peekFlowResult = awaitRpcFlowFinished(charlieHoldingId, peekFlowId)
        assertThat(peekFlowResult.flowError).isNull()
        assertThat(peekFlowResult.flowResult).isNotNull()

        val parsedPeekFlowResult = objectMapper
            .readValue(peekFlowResult.flowResult, PeekTransactionResponse::class.java)

        assertThat(parsedPeekFlowResult.errorMessage).isNull()
        assertThat(parsedPeekFlowResult.inputs).singleElement().extracting { it.testField }.isEqualTo(input)
        assertThat(parsedPeekFlowResult.outputs).singleElement().extracting { it.testField }.isEqualTo("Populated by responder")
    }


    @Test
    fun `Utxo Ledger - creating a transaction that fails custom validation causes finality to fail`() {
        val utxoFlowRequestId = startRpcFlow(
            aliceHoldingId,
            mapOf("input" to "fail", "members" to listOf(bobX500, charlieX500), "notary" to notaryX500),
            "net.cordapp.demo.utxo.UtxoDemoFlow"
        )
        val utxoFlowResult = awaitRpcFlowFinished(aliceHoldingId, utxoFlowRequestId)
        assertThat(utxoFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(utxoFlowResult.flowResult).contains("Transaction validation failed for transaction")
        assertThat(utxoFlowResult.flowResult).contains("when signature was requested")
    }

    data class TestUtxoStateResult(val testField: String, val participants: List<ByteArray>)

    data class UtxoTransactionResult(
        val id: SecureHash,
        val states: List<TestUtxoStateResult>,
        val participants: List<ByteArray>
    )

    data class FindTransactionResponse(
        val transaction: UtxoTransactionResult?,
        val errorMessage: String?
    )

    data class EvolveResponse(
        val transactionId: String?,
        val errorMessage: String?)

    data class PeekTransactionResponse(
        val inputs: List<TestUtxoStateResult>,
        val outputs: List<TestUtxoStateResult>,
        val errorMessage: String?
    )

}

