package net.corda.applications.workers.smoketest.ledger

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.crypto.core.parseSecureHash
import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.RPC_FLOW_STATUS_SUCCESS
import net.corda.e2etest.utilities.TEST_NOTARY_CPB_LOCATION
import net.corda.e2etest.utilities.TEST_NOTARY_CPI_NAME
import net.corda.e2etest.utilities.TestRequestIdGenerator
import net.corda.e2etest.utilities.awaitRpcFlowFinished
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.e2etest.utilities.startRpcFlow
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.time.Duration
import java.util.UUID

@Suppress("Unused", "FunctionName")
@TestInstance(PER_CLASS)
class UtxoLedgerTests : ClusterReadiness by ClusterReadinessChecker() {

    private companion object {
        const val TEST_CPI_NAME = "ledger-utxo-demo-app"
        const val TEST_CPB_LOCATION = "/META-INF/ledger-utxo-demo-app.cpb"
        const val NOTARY_SERVICE_X500 = "O=MyNotaryService, L=London, C=GB"

        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            val module = SimpleModule()
            module.addSerializer(SecureHash::class.java, SecureHashSerializer)
            module.addDeserializer(SecureHash::class.java, SecureHashDeserializer)
            registerModule(module)
        }
    }

    private val testRunUniqueId = UUID.randomUUID()
    private val groupId = UUID.randomUUID().toString()
    private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
    private val notaryCpiName = "${TEST_NOTARY_CPI_NAME}_$testRunUniqueId"

    private val aliceX500 = "CN=Alice-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val charlieX500 = "CN=Charlie-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val notaryX500 = "CN=Notary-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"

    private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, groupId)
    private val bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
    private val charlieHoldingId: String = getHoldingIdShortHash(charlieX500, groupId)
    private val notaryHoldingId: String = getHoldingIdShortHash(notaryX500, groupId)

    private val staticMemberList = listOf(
        aliceX500,
        bobX500,
        charlieX500,
        notaryX500
    )

    @BeforeAll
    fun beforeAll() {
        // check cluster is ready
        assertIsReady(Duration.ofMinutes(1), Duration.ofMillis(100))

        DEFAULT_CLUSTER.conditionallyUploadCpiSigningCertificate()

        conditionallyUploadCordaPackage(
            cpiName,
            TEST_CPB_LOCATION,
            groupId,
            staticMemberList
        )
        conditionallyUploadCordaPackage(
            notaryCpiName,
            TEST_NOTARY_CPB_LOCATION,
            groupId,
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
        registerStaticMember(notaryHoldingId, NOTARY_SERVICE_X500)
    }


    @Test
    fun `Utxo Ledger - create a transaction containing states and finalize it then evolve it`(testInfo: TestInfo) {
        val idGenerator = TestRequestIdGenerator(testInfo)
        val input = "test input"
        val utxoFlowRequestId = startRpcFlow(
            aliceHoldingId,
            mapOf("input" to input, "members" to listOf(bobX500, charlieX500), "notary" to NOTARY_SERVICE_X500),
            "com.r3.corda.demo.utxo.UtxoDemoFlow",
            requestId = idGenerator.nextId
        )
        val utxoFlowResult = awaitRpcFlowFinished(aliceHoldingId, utxoFlowRequestId)
        assertThat(utxoFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(utxoFlowResult.flowError).isNull()

        for (holdingId in listOf(aliceHoldingId, bobHoldingId, charlieHoldingId)) {
            val findTransactionFlowRequestId = startRpcFlow(
                holdingId,
                mapOf("transactionId" to utxoFlowResult.flowResult!!),
                "com.r3.corda.demo.utxo.FindTransactionFlow",
                requestId = idGenerator.nextId
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
            "com.r3.corda.demo.utxo.UtxoDemoEvolveFlow",
            requestId = idGenerator.nextId

        )
        val evolveFlowResult = awaitRpcFlowFinished(bobHoldingId, evolveRequestId)

        val parsedEvolveFlowResult = objectMapper
            .readValue(evolveFlowResult.flowResult!!, EvolveResponse::class.java)
        assertThat(parsedEvolveFlowResult.transactionId).isNotNull()
        assertThat(parsedEvolveFlowResult.errorMessage).isNull()
        assertThat(evolveFlowResult.flowError).isNull()

        // Peek into the last transaction

        val peekFlowId = startRpcFlow(
            bobHoldingId,
            mapOf("transactionId" to parsedEvolveFlowResult.transactionId!!),
            "com.r3.corda.demo.utxo.PeekTransactionFlow",
            requestId = idGenerator.nextId
        )

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
    fun `Utxo Ledger - creating a transaction that fails custom validation causes finality to fail`(testInfo: TestInfo) {
        val idGenerator = TestRequestIdGenerator(testInfo)
        val utxoFlowRequestId = startRpcFlow(
            aliceHoldingId,
            mapOf("input" to "fail", "members" to listOf(bobX500, charlieX500), "notary" to NOTARY_SERVICE_X500),
            "com.r3.corda.demo.utxo.UtxoDemoFlow",
            requestId = idGenerator.nextId
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
        val errorMessage: String?
    )

    data class PeekTransactionResponse(
        val inputs: List<TestUtxoStateResult>,
        val outputs: List<TestUtxoStateResult>,
        val errorMessage: String?
    )

    data class CustomQueryFlowResponse(val results: List<String>)

    internal object SecureHashSerializer : com.fasterxml.jackson.databind.JsonSerializer<SecureHash>() {
        override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    internal object SecureHashDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<SecureHash>() {
        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): SecureHash {
            return parseSecureHash(parser.text)
        }
    }
}
