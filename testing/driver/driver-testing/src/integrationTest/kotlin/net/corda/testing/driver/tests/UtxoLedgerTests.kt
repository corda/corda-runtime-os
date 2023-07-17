package net.corda.testing.driver.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.r3.corda.demo.utxo.FindTransactionFlow
import com.r3.corda.demo.utxo.FindTransactionParameters
import com.r3.corda.demo.utxo.FindTransactionResponse
import com.r3.corda.demo.utxo.PeekTransactionFlow
import com.r3.corda.demo.utxo.PeekTransactionParameters
import com.r3.corda.demo.utxo.PeekTransactionResponse
import com.r3.corda.demo.utxo.TestUtxoStateResult
import com.r3.corda.demo.utxo.UtxoCustomQueryDemoFlow
import com.r3.corda.demo.utxo.UtxoCustomQueryDemoFlow.CustomQueryFlowRequest
import com.r3.corda.demo.utxo.UtxoCustomQueryDemoFlow.CustomQueryFlowResponse
import com.r3.corda.demo.utxo.UtxoDemoEvolveFlow
import com.r3.corda.demo.utxo.UtxoDemoFlow
import java.util.concurrent.TimeUnit.MINUTES
import net.corda.crypto.core.parseSecureHash
import net.corda.testing.driver.DriverNodes
import net.corda.testing.driver.runFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory

@Suppress("FunctionName")
@Timeout(5, unit = MINUTES)
@TestInstance(PER_CLASS)
class UtxoLedgerTests {
    private companion object {
        private const val EVOLVED_INPUT = "evolved input"
        private const val TEST_INPUT = "test data"
        private const val FAIL_INPUT = "fail"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val alice = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB")
    private val bob = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US")
    private val charlie = MemberX500Name.parse("CN=Charlie, OU=Testing, O=R3, L=Paris, C=FR")
    private val notary = MemberX500Name.parse("CN=Notary, OU=Testing, O=R3, L=Rome, C=IT")
    private val utxoLedger = mutableMapOf<MemberX500Name, VirtualNodeInfo>()
    private val jsonMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())

        val module = SimpleModule().apply {
            addSerializer(SecureHash::class.java, SecureHashSerializer)
            addDeserializer(SecureHash::class.java, SecureHashDeserializer)
        }
        registerModule(module)
    }

    @Suppress("JUnitMalformedDeclaration")
    @RegisterExtension
    private val driver = DriverNodes(alice, bob, charlie).withNotary(notary, 1).forAllTests()

    @BeforeAll
    fun start() {
        driver.run { dsl ->
            dsl.startNodes(setOf(alice, bob, charlie)).onEach { vNode ->
                logger.info("VirtualNode({}): {}", vNode.holdingIdentity.x500Name, vNode)
            }.filter { vNode ->
                vNode.cpiIdentifier.name == "ledger-utxo-demo-app"
            }.associateByTo(utxoLedger) { vNode ->
                vNode.holdingIdentity.x500Name
            }
            assertThat(utxoLedger).hasSize(3)
        }
        logger.info("{}, {} and {} started successfully", alice.commonName, bob.commonName, charlie.commonName)
    }

    @Test
    fun `custom query can be executed and results are returned if no offset is provided and limit is maximized`() {
        // Issue some states and consume them
        for (i in 0 until 2) {
            val inputResult = driver.let { dsl ->
                dsl.runFlow<UtxoDemoFlow>(utxoLedger[alice] ?: fail("Missing vNode for Alice")) {
                    val request = UtxoDemoFlow.InputMessage(
                        input = TEST_INPUT,
                        members = listOf(bob.toString(), charlie.toString()),
                        notary = notary.toString()
                    )
                    jsonMapper.writeValueAsString(request)
                }
            } ?: fail("inputResult must not be null")
            logger.info("UTXO Demo={}", inputResult)
            parseSecureHash(inputResult)
        }

        val customQueryResult = driver.let { dsl ->
            dsl.runFlow<UtxoCustomQueryDemoFlow>(utxoLedger[alice] ?: fail("Missing vNode for Alice")) {
                val request = CustomQueryFlowRequest(
                    offset = 0,
                    limit = 100,
                    testField = TEST_INPUT
                )
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("customQueryResult must not be null")
        logger.info("customQueryResult: {}", customQueryResult)

        val customQueryFlowResponse = jsonMapper.readValue<CustomQueryFlowResponse?>(customQueryResult)
            ?: fail("CustomQueryFlowResponse is null")
        assertThat(customQueryFlowResponse.results).hasSizeGreaterThan(1)
    }

    @Test
    fun `create a transaction containing states and finalize it then evolve it`() {
        val inputResult = driver.let { dsl ->
            dsl.runFlow<UtxoDemoFlow>(utxoLedger[alice] ?: fail("Missing vNode for Alice")) {
                val request = UtxoDemoFlow.InputMessage(
                    input = TEST_INPUT,
                    members = listOf(bob.toString(), charlie.toString()),
                    notary = notary.toString()
                )
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("inputResult must not be null")
        logger.info("UTXO Demo={}", inputResult)
        val txnId = parseSecureHash(inputResult)

        for (member in listOf(alice, bob, charlie)) {
            val flowResult = driver.let { dsl ->
                dsl.runFlow<FindTransactionFlow>(utxoLedger[member] ?: fail("Missing vNode for ${member.commonName}")) {
                    val request = FindTransactionParameters(txnId.toString())
                    jsonMapper.writeValueAsString(request)
                }
            } ?: fail("flowResult must not be null")
            logger.info("{}: {}", member.commonName, flowResult)

            val transactionResponse = jsonMapper.readValue<FindTransactionResponse?>(flowResult)
                ?: fail("FindTransactionResponse for ${member.commonName} is null")
            assertThat(transactionResponse.errorMessage).isNull()

            val transaction = transactionResponse.transaction
                ?: fail("transaction for ${member.commonName} is null")
            assertThat(transaction.id).isEqualTo(txnId)
            assertThat(transaction.states.map(TestUtxoStateResult::testField)).containsOnly(TEST_INPUT)
            assertThat(transaction.states.flatMap(TestUtxoStateResult::participants)).hasSize(3)
            assertThat(transaction.participants).hasSize(3)
        }

        val evolveResult = driver.let { dsl ->
            dsl.runFlow<UtxoDemoEvolveFlow>(utxoLedger[bob] ?: fail("Missing vNode for Bob")) {
                val request = UtxoDemoEvolveFlow.EvolveMessage(
                    update = EVOLVED_INPUT,
                    transactionId = txnId.toString(),
                    index = 0
                )
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("evolveResult must not be null")
        logger.info("UTXO Evolve Demo={}", evolveResult)

        val evolveFlowResponse = jsonMapper.readValue<UtxoDemoEvolveFlow.EvolveResponse>(evolveResult)
        assertThat(evolveFlowResponse.errorMessage).isNull()

        val evolveTxnId = parseSecureHash(evolveFlowResponse.transactionId
            ?: fail("evolve transactionId must not be null"))

        // Peek into the last transaction

        val peekResult = driver.let { dsl ->
            dsl.runFlow<PeekTransactionFlow>(utxoLedger[bob] ?: fail("Missing vNode for Bob")) {
                val request = PeekTransactionParameters(evolveTxnId.toString())
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("peekResult must not be null")
        logger.info("UTXO Peek Transaction={}", peekResult)

        val peekTransactionResponse = jsonMapper.readValue<PeekTransactionResponse>(peekResult)

        assertThat(peekTransactionResponse.errorMessage).isNull()
        assertThat(peekTransactionResponse.inputs)
            .singleElement()
            .extracting(TestUtxoStateResult::testField)
            .isEqualTo(TEST_INPUT)
        assertThat(peekTransactionResponse.outputs)
            .singleElement()
            .extracting(TestUtxoStateResult::testField)
            .isEqualTo(EVOLVED_INPUT)
    }

    @Test
    fun `creating a transaction that fails custom validation causes finality to fail`() {
        val inputResult = driver.let { dsl ->
            dsl.runFlow<UtxoDemoFlow>(utxoLedger[alice] ?: fail("Missing vNode for Alice")) {
                val request = UtxoDemoFlow.InputMessage(
                    input = FAIL_INPUT,
                    members = listOf(bob.toString(), charlie.toString()),
                    notary  = notary.toString()
                )
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("inputResult should not be null")
        logger.info("UTXO Demo={}", inputResult)

        assertThat(inputResult)
            .contains("Transaction validation failed for transaction ")
            .contains(" when signature was requested")
    }
}
