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
import net.corda.e2etest.utilities.REST_FLOW_STATUS_SUCCESS
import net.corda.e2etest.utilities.TEST_NOTARY_CPB_LOCATION
import net.corda.e2etest.utilities.TEST_NOTARY_CPI_NAME
import net.corda.e2etest.utilities.TestRequestIdGenerator
import net.corda.e2etest.utilities.awaitRestFlowFinished
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.e2etest.utilities.startRestFlow
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
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

    private val testRunUniqueId = "r1" // UUID.randomUUID()
    private val groupId = "083edf6d-0f7d-435d-9dc3-b7db07b92821" // UUID.randomUUID().toString()
    private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
    private val notaryCpiName = "${TEST_NOTARY_CPI_NAME}_$testRunUniqueId"

    private val aliceX500 = "CN=Alice-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val charlieX500 = "CN=Charlie-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val notaryX500 = "CN=Notary-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val extraParties = 50
    private val extraPartiesX500 = (0 until extraParties).map { "CN=Extra-${it}-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB" }

    private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, groupId)
    private val bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
    private val charlieHoldingId: String = getHoldingIdShortHash(charlieX500, groupId)
    private val notaryHoldingId: String = getHoldingIdShortHash(notaryX500, groupId)
    private val extraPartiesHoldingIds: List<String> = extraPartiesX500.map { getHoldingIdShortHash(it, groupId) }

    private val staticMemberList = listOf(
        aliceX500,
        bobX500,
        charlieX500,
        notaryX500
    ) + extraPartiesX500

    @BeforeAll
    fun beforeAll() {
        // check cluster is ready
        assertIsReady(Duration.ofMinutes(2), Duration.ofMillis(100))

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
        val extraPartiesActualHoldingIds = extraPartiesX500.map { getOrCreateVirtualNodeFor(it, cpiName) }
        val notaryActualHoldingId = getOrCreateVirtualNodeFor(notaryX500, notaryCpiName)

        assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)
        (0 until extraParties).forEach {
            assertThat(extraPartiesActualHoldingIds[it]).isEqualTo(extraPartiesHoldingIds[it])
        }
        assertThat(notaryActualHoldingId).isEqualTo(notaryHoldingId)

        registerStaticMember(aliceHoldingId)
        registerStaticMember(bobHoldingId)
        registerStaticMember(charlieHoldingId)
        extraPartiesHoldingIds.forEach { registerStaticMember(it) }
        registerStaticMember(notaryHoldingId, NOTARY_SERVICE_X500)
    }


    @Test
    fun `Utxo Ledger - create a transaction containing states and finalize it then evolve it`(testInfo: TestInfo) {
        val timings = mutableListOf<Long>()
        val idGenerator = TestRequestIdGenerator(testInfo)
        val input = "test input"
        val utxoFlowRequestId = startRestFlow(
            aliceHoldingId,
            mapOf("input" to input, "members" to listOf(bobX500, charlieX500), "notary" to NOTARY_SERVICE_X500),
            "com.r3.corda.demo.utxo.UtxoDemoFlow",
            requestId = idGenerator.nextId
        )
        val utxoFlowResult = awaitRestFlowFinished(aliceHoldingId, utxoFlowRequestId)
        assertThat(utxoFlowResult.flowStatus).isEqualTo(REST_FLOW_STATUS_SUCCESS)
        assertThat(utxoFlowResult.flowError).isNull()

        for (holdingId in listOf(aliceHoldingId, bobHoldingId, charlieHoldingId)) {
            val findTransactionFlowRequestId = startRestFlow(
                holdingId,
                mapOf("transactionId" to utxoFlowResult.flowResult!!),
                "com.r3.corda.demo.utxo.FindTransactionFlow",
                requestId = idGenerator.nextId
            )
            val transactionResult = awaitRestFlowFinished(holdingId, findTransactionFlowRequestId)
            assertThat(transactionResult.flowStatus).isEqualTo(REST_FLOW_STATUS_SUCCESS)
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
        var currentTransactionId = checkNotNull(utxoFlowResult.flowResult)
        var message = input
        var prevInput = ""
        val constant = true // if true, keep the number of participants at 4, if false add one on each evolution
        for (stage in 1 until extraParties) {
            val start = Instant.now()
            val evolvedMessage = "evolved input $stage"
            val addList: List<String> = if (constant) { if (stage == 1) listOf(extraPartiesX500[stage-1]) else emptyList() } else listOf(extraPartiesX500[stage-1])
            val removeList: List<String> = if (constant) emptyList() else  (if (stage >= 2) listOf(extraPartiesX500[stage-2]) else emptyList())
            println("adding participants $addList  and removing participants $removeList")
            val evolveRequestId = startRestFlow(
                bobHoldingId,
                mapOf(
                    "update" to evolvedMessage, "transactionId" to currentTransactionId, "index" to "0",
                    "addParticipants" to addList,
                    "removeParticipants" to removeList
                ),
                "com.r3.corda.demo.utxo.UtxoDemoEvolveFlow",
                requestId = idGenerator.nextId

            )
            val evolveFlowResult = awaitRestFlowFinished(bobHoldingId, evolveRequestId)
            val evolveFlowResultInner = checkNotNull(evolveFlowResult.flowResult) {"evolve failed"}
            assertThat(evolveFlowResult.flowError).isNull()
            // Peek into the last transaction
            val parsedEvolveFlowResultInner = objectMapper.readValue(evolveFlowResultInner, EvolveResponse::class.java)
            assertThat(parsedEvolveFlowResultInner.transactionId).isNotNull()
            assertThat(parsedEvolveFlowResultInner.errorMessage).isNull()

            currentTransactionId = checkNotNull(parsedEvolveFlowResultInner.transactionId) { "no flow result" }
            prevInput = message
            message = evolvedMessage
            val end = Instant.now()
            val duration = ChronoUnit.MILLIS.between(start, end)
            println("TTTTTT completed stage $stage in ${duration}ms")
            timings += duration
            println(timings)
        }


        val peekFlowId = startRestFlow(
            bobHoldingId,
            mapOf("transactionId" to currentTransactionId),
            "com.r3.corda.demo.utxo.PeekTransactionFlow",
            requestId = idGenerator.nextId
        )

        val peekFlowResult = awaitRestFlowFinished(bobHoldingId, peekFlowId)
        assertThat(peekFlowResult.flowError).isNull()
        assertThat(peekFlowResult.flowResult).isNotNull()

        val parsedPeekFlowResult = objectMapper
            .readValue(peekFlowResult.flowResult, PeekTransactionResponse::class.java)

        assertThat(parsedPeekFlowResult.errorMessage).isNull()
        assertThat(parsedPeekFlowResult.inputs).singleElement().extracting { it.testField }.isEqualTo(prevInput)
        assertThat(parsedPeekFlowResult.outputs).singleElement().extracting { it.testField }.isEqualTo(message)
        println("done; final timings are:")
        println(timings)
    }

    @Test
    fun `Utxo Ledger - creating a transaction that fails custom validation causes finality to fail`(testInfo: TestInfo) {
        val idGenerator = TestRequestIdGenerator(testInfo)
        val utxoFlowRequestId = startRestFlow(
            aliceHoldingId,
            mapOf("input" to "fail", "members" to listOf(bobX500, charlieX500), "notary" to NOTARY_SERVICE_X500),
            "com.r3.corda.demo.utxo.UtxoDemoFlow",
            requestId = idGenerator.nextId
        )
        val utxoFlowResult = awaitRestFlowFinished(aliceHoldingId, utxoFlowRequestId)
        assertThat(utxoFlowResult.flowStatus).isEqualTo(REST_FLOW_STATUS_SUCCESS)
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
