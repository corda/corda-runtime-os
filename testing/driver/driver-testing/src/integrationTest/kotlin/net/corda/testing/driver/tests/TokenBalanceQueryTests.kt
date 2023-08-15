package net.corda.testing.driver.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.r3.corda.demo.utxo.token.selection.TokenBalanceQueryFlow
import java.math.BigDecimal
import java.util.concurrent.TimeUnit.MINUTES
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
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory

@Suppress("FunctionName")
@Timeout(5, unit = MINUTES)
@TestInstance(PER_CLASS)
class TokenBalanceQueryTests {
    private companion object {
        private const val ISSUER_CURRENCY = "USD"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val alice = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB")
    private val bob = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US")
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
    private val driver = DriverNodes(alice, bob).withNotary(notary, 1).forAllTests()

    @BeforeAll
    fun start() {
        driver.run { dsl ->
            dsl.startNodes(setOf(alice, bob)).onEach { vNode ->
                logger.info("VirtualNode({}): {}", vNode.holdingIdentity.x500Name, vNode)
            }.filter { vNode ->
                vNode.cpiIdentifier.name == "ledger-utxo-demo-app"
            }.associateByTo(utxoLedger) { vNode ->
                vNode.holdingIdentity.x500Name
            }
            assertThat(utxoLedger).hasSize(2)
        }
        logger.info("{} and {} started successfully", alice.commonName, bob.commonName)
    }

    @Test
    fun `ensure it is possible to send a balance query request and receive a response`() {
         val tokenBalanceQueryResult = driver.let { dsl ->
            dsl.runFlow<TokenBalanceQueryFlow>(utxoLedger[alice] ?: fail("Missing vNode for Alice")) {
                val request = TokenBalanceQueryRequest(
                    tokenType = "com.r3.corda.demo.utxo.contract.CoinState",
                    issuerBankX500 = bob.toString(),
                    currency = ISSUER_CURRENCY
                )
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("tokenBalanceQueryResult must not be null")
        logger.info("tokenBalanceQueryResult: {}", tokenBalanceQueryResult)

        val tokenBalanceQueryResponse = jsonMapper.readValue<TokenBalanceQueryResponse?>(tokenBalanceQueryResult)
            ?: fail("TokenBalanceQueryResponse is null")

        // Check that the balance of the token cache is zero since no token has been created
        assertAll(
            { assertThat(tokenBalanceQueryResponse.availableBalance).isEqualTo(BigDecimal.ZERO) },
            { assertThat(tokenBalanceQueryResponse.totalBalance).isEqualTo(BigDecimal.ZERO) }
        )
    }

    data class TokenBalanceQueryRequest(val tokenType: String, val issuerBankX500: String, val currency: String)

    data class TokenBalanceQueryResponse(
        val availableBalance: BigDecimal,
        val totalBalance: BigDecimal
    )
}
