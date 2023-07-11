package net.corda.testing.driver.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.r3.corda.demo.consensual.ConsensualDemoFlow
import com.r3.corda.demo.consensual.FindTransactionFlow
import com.r3.corda.demo.consensual.FindTransactionParameters
import com.r3.corda.demo.consensual.FindTransactionResponse
import com.r3.corda.demo.consensual.TestConsensualStateResult
import java.util.concurrent.TimeUnit.MINUTES
import net.corda.crypto.core.parseSecureHash
import net.corda.testing.driver.DriverNodes
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

@Suppress("FunctionName", "JUnitMalformedDeclaration")
@Timeout(5, unit = MINUTES)
@TestInstance(PER_CLASS)
class ConsensualLedgerTests {
    private companion object {
        private const val TEST_INPUT = "test data"
        private const val FAIL_INPUT = "fail"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val alice = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB")
    private val bob = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US")
    private val charlie = MemberX500Name.parse("CN=Charlie, OU=Testing, O=R3, L=Paris, C=FR")
    private val consensualLedger = mutableMapOf<MemberX500Name, VirtualNodeInfo>()
    private val jsonMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())

        val module = SimpleModule().apply {
            addSerializer(SecureHash::class.java, SecureHashSerializer)
            addDeserializer(SecureHash::class.java, SecureHashDeserializer)
        }
        registerModule(module)
    }

    @RegisterExtension
    private val driver = DriverNodes(alice, bob, charlie).forAllTests()

    @BeforeAll
    fun start() {
        val virtualNodes = mutableSetOf<VirtualNodeInfo>()
        driver.run { dsl ->
            virtualNodes += dsl.startNode(setOf(alice, bob, charlie)).onEach { vNode ->
                logger.info("VirtualNode({}): {}", vNode.holdingIdentity.x500Name, vNode)
            }
        }
        logger.info("{}, {} and {} started successfully", alice.commonName, bob.commonName, charlie.commonName)

        virtualNodes.filter { vNode ->
            vNode.cpiIdentifier.name == "ledger-consensual-demo-app"
        }.associateByTo(consensualLedger) { vNode ->
            vNode.holdingIdentity.x500Name
        }
        assertThat(consensualLedger).hasSize(3)
    }

    @Test
    fun `create a transaction containing states and finalize it`() {
        val inputResult = driver.let { dsl ->
            dsl.runFlow(consensualLedger[alice] ?: fail("Missing vNode for Alice"), ConsensualDemoFlow::class.java) {
                val request = ConsensualDemoFlow.InputMessage(TEST_INPUT, listOf(bob.toString(), charlie.toString()))
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("inputResult must not be null")
        logger.info("Consensual Demo={}", inputResult)
        val txnId = parseSecureHash(inputResult)

        for (member in listOf(alice, bob, charlie)) {
            val flowResult = driver.let { dsl ->
                dsl.runFlow(consensualLedger[member] ?: fail("Missing vNode for ${member.commonName}"), FindTransactionFlow::class.java) {
                    val request = FindTransactionParameters(txnId.toString())
                    jsonMapper.writeValueAsString(request)
                }
            } ?: fail("flowResult must not be null")
            logger.info("{}: {}", member.commonName, flowResult)

            val transactionResponse = jsonMapper.readValue(flowResult, FindTransactionResponse::class.java)
                ?: fail("FindTransactionResponse for ${member.commonName} is null")
            assertThat(transactionResponse.errorMessage).isNull()

            val transaction = transactionResponse.transaction
                ?: fail("transaction for ${member.commonName} is null")
            assertThat(transaction.id).isEqualTo(txnId)
            assertThat(transaction.states.map(TestConsensualStateResult::testField)).containsOnly(TEST_INPUT)
            assertThat(transaction.states.flatMap(TestConsensualStateResult::participants)).hasSize(3)
            assertThat(transaction.participants).hasSize(3)
        }
    }

    @Test
    fun `creating a transaction that fails custom validation causes finality to fail`() {
        val inputResult = driver.let { dsl ->
            dsl.runFlow(consensualLedger[alice] ?: fail("Missing vNode for Alice"), ConsensualDemoFlow::class.java) {
                val request = ConsensualDemoFlow.InputMessage(FAIL_INPUT, listOf(bob.toString(), charlie.toString()))
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("inputResult should not be null")
        logger.info("Consensual Demo={}", inputResult)

        assertThat(inputResult)
            .contains("Transaction validation failed for transaction ")
            .contains(" when signature was requested")
    }
}
