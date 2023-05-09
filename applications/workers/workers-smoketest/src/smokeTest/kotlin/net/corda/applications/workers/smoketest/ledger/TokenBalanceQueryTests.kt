package net.corda.applications.workers.smoketest.ledger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.math.BigDecimal
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

@Suppress("Unused", "FunctionName")
@TestInstance(PER_CLASS)
class TokenBalanceQueryTests {

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

    private data class TokenQueryBalanceResponseMsg(
        val balance: BigDecimal,
        val balanceIncludingClaimedTokens: BigDecimal
    )

    private val testRunUniqueId = "1"//UUID.randomUUID()
    private val groupId = "b3de5521-9aef-453f-9f94-62f0d86962a2"
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

    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }

    private val staticMemberList = listOf(
        aliceX500,
        bobX500,
        charlieX500,
        notaryX500
    )

    private fun convertToTokenQueryBalanceResponseMsg(tokenQueryBalanceResponseMsgStr: String) =
        objectMapper.readValue(
            tokenQueryBalanceResponseMsgStr,
            TokenQueryBalanceResponseMsg::class.java
        )

    private fun runTokenQueryBalanceFlow(): TokenQueryBalanceResponseMsg {

        val tokenQueryBalanceFlowName = "com.r3.corda.demo.utxo.TokenBalanceQueryFlow"

        val tokenQueryBalanceRpcStartArgs = mapOf(
            "tokenType" to "com.r3.corda.demo.utxo.contract.CoinState",
            "issuerBankX500" to charlieX500,
            "currency" to "USD"
        )

        val flowRequestId = startRpcFlow(aliceHoldingId, tokenQueryBalanceRpcStartArgs, tokenQueryBalanceFlowName)

        val flowResult = awaitRpcFlowFinished(aliceHoldingId, flowRequestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

        return convertToTokenQueryBalanceResponseMsg(flowResult.flowResult!!)
    }


    @BeforeAll
    fun beforeAll() {
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

        registerStaticMember(notaryHoldingId, true)
    }

    @Test
    fun `Token Query Balance - Ensure balance is zero`() {

        val tokenQueryBalance = runTokenQueryBalanceFlow()

        assertThat(tokenQueryBalance.balance).isEqualTo(BigDecimal.ZERO)
        assertThat(tokenQueryBalance.balanceIncludingClaimedTokens).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `Token Query Balance - Create 10 coins`() {
        val rpcStartArgs = mapOf(
            "issuerBankX500" to charlieX500,
            "currency" to "USD",
            "numberOfCoins" to 10,
            "valueOfCoin" to 1,
            "tag" to "simple coin"
        )

        val flowRequestId = startRpcFlow(
            aliceHoldingId,
            rpcStartArgs,
            "com.r3.corda.demo.utxo.token.CreateCoinFlow"
        )

        val flowResult = awaitRpcFlowFinished(aliceHoldingId, flowRequestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
    }

    @Test
    fun `Token Query Balance - Ensure balance is equal to 10`() {
        val tokenQueryBalance = runTokenQueryBalanceFlow()

        assertThat(tokenQueryBalance.balance).isEqualTo(BigDecimal.TEN)
        assertThat(tokenQueryBalance.balanceIncludingClaimedTokens).isEqualTo(BigDecimal.TEN)
    }

    @Test
    fun `Token Query Balance - Claim token`() {
        val rpcStartArgs = emptyMap<String,String>()

        val flowRequestId = startRpcFlow(
            aliceHoldingId,
            rpcStartArgs,
            "com.r3.corda.demo.utxo.UtxoBackchainResolutionDemoFlow"
        )

        val flowResult = awaitRpcFlowFinished(aliceHoldingId, flowRequestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
    }

    @Test
    fun `Token Query Balance - Ensure balance is equal to 5`() {
        val rpcStartArgs = emptyMap<String,String>()

        val flowRequestId = startRpcFlow(
            aliceHoldingId,
            rpcStartArgs,
            "com.r3.corda.demo.utxo.UtxoBackchainResolutionDemoFlow"
        )

        println("claiming token coins for bank = ${charlieHoldingId} vnode = ${aliceHoldingId}")

        val flowResult = awaitRpcFlowFinished(aliceHoldingId, flowRequestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
    }
}
