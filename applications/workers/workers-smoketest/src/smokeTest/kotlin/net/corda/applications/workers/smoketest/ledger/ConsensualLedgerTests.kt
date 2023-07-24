package net.corda.applications.workers.smoketest.ledger

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.applications.workers.smoketest.virtualnode.VirtualNodeInitializer
import net.corda.crypto.core.parseSecureHash
import net.corda.e2etest.utilities.RPC_FLOW_STATUS_SUCCESS
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
class ConsensualLedgerTests {

    private companion object {
        const val TEST_CPI_NAME = "ledger-consensual-demo-app"
        const val TEST_CPB_LOCATION = "/META-INF/ledger-consensual-demo-app.cpb"

        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            val module = SimpleModule()
            module.addSerializer(SecureHash::class.java, SecureHashSerializer)
            module.addDeserializer(SecureHash::class.java, SecureHashDeserializer)
            registerModule(module)
        }

        private val vNodeInitializer = VirtualNodeInitializer
    }

    private val testRunUniqueId = UUID.randomUUID()
    private val groupId = UUID.randomUUID().toString()
    private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"

    private val aliceX500 = "CN=Alice-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
    private val charlieX500 = "CN=Charlie-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"

    private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, groupId)
    private val bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
    private val charlieHoldingId: String = getHoldingIdShortHash(charlieX500, groupId)

    private val staticMemberList = listOf(
        aliceX500,
        bobX500,
        charlieX500
    )

    @BeforeAll
    fun beforeAll() {
        conditionallyUploadCordaPackage(
            cpiName,
            TEST_CPB_LOCATION,
            groupId,
            staticMemberList
        )

        val aliceActualHoldingId = getOrCreateVirtualNodeFor(aliceX500, cpiName)
        val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)
        val charlieActualHoldingId = getOrCreateVirtualNodeFor(charlieX500, cpiName)

        assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)

        registerStaticMember(aliceHoldingId)
        registerStaticMember(bobHoldingId)
        registerStaticMember(charlieHoldingId)
    }

    @Test
    fun `Consensual Ledger - create a transaction containing states and finalize it`() {
        val input = "test input"
        val consensualFlowRequestId = startRpcFlow(
            aliceHoldingId,
            mapOf("input" to input, "members" to listOf(bobX500, charlieX500)),
            "com.r3.corda.demo.consensual.ConsensualDemoFlow"
        )
        val consensualFlowResult = awaitRpcFlowFinished(aliceHoldingId, consensualFlowRequestId)
        assertThat(consensualFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(consensualFlowResult.flowError).isNull()

        for (holdingId in listOf(aliceHoldingId, bobHoldingId, charlieHoldingId)) {
            val findTransactionFlowRequestId = startRpcFlow(
                holdingId,
                mapOf("transactionId" to consensualFlowResult.flowResult!!),
                "com.r3.corda.demo.consensual.FindTransactionFlow"
            )
            val transactionResult = awaitRpcFlowFinished(holdingId, findTransactionFlowRequestId)
            assertThat(transactionResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            assertThat(transactionResult.flowError).isNull()

            val parsedResult = objectMapper
                .readValue(transactionResult.flowResult!!, FindTransactionResponse::class.java)

            assertThat(parsedResult.transaction).withFailMessage {
                "Member with holding identity $holdingId did not receive the transaction"
            }.isNotNull
            assertThat(parsedResult.transaction!!.id.toString()).isEqualTo(consensualFlowResult.flowResult)
            assertThat(parsedResult.transaction.states.map { it.testField }).containsOnly(input)
            assertThat(parsedResult.transaction.states.flatMap { it.participants }).hasSize(3)
            assertThat(parsedResult.transaction.participants).hasSize(3)
        }
    }

    @Test
    fun `Consensual Ledger - creating a transaction that fails custom validation causes finality to fail`() {
        val consensualFlowRequestId = startRpcFlow(
            aliceHoldingId,
            mapOf("input" to "fail", "members" to listOf(bobX500, charlieX500)),
            "com.r3.corda.demo.consensual.ConsensualDemoFlow"
        )
        val consensualFlowResult = awaitRpcFlowFinished(aliceHoldingId, consensualFlowRequestId)
        assertThat(consensualFlowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(consensualFlowResult.flowResult).contains("Transaction validation failed for transaction")
        assertThat(consensualFlowResult.flowResult).contains("when signature was requested")
    }

    data class TestConsensualStateResult(val testField: String, val participants: List<ByteArray>)

    data class ConsensualTransactionResult(
        val id: SecureHash,
        val states: List<TestConsensualStateResult>,
        val participants: List<ByteArray>
    )

    data class FindTransactionResponse(
        val transaction: ConsensualTransactionResult?,
        val errorMessage: String?
    )
}

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
