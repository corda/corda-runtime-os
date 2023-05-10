package net.corda.applications.workers.smoketest.ledger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.math.BigDecimal
import net.corda.e2etest.utilities.CLUSTER_URI
import net.corda.e2etest.utilities.CODE_SIGNER_CERT
import net.corda.e2etest.utilities.PASSWORD
import net.corda.e2etest.utilities.RPC_FLOW_STATUS_SUCCESS
import net.corda.e2etest.utilities.TEST_NOTARY_CPB_LOCATION
import net.corda.e2etest.utilities.TEST_NOTARY_CPI_NAME
import net.corda.e2etest.utilities.USERNAME
import net.corda.e2etest.utilities.assertWithRetry
import net.corda.e2etest.utilities.awaitRpcFlowFinished
import net.corda.e2etest.utilities.cluster
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.e2etest.utilities.startRpcFlow
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS

@Suppress("Unused", "FunctionName")
@TestInstance(PER_CLASS)
class TokenBalanceQueryTests {

    private companion object {
        const val TEST_CPI_NAME = "ledger-utxo-demo-app"
        const val TEST_CPB_LOCATION = "/META-INF/ledger-utxo-demo-app.cpb"

//        val objectMapper = ObjectMapper().apply {
//            registerModule(KotlinModule.Builder().build())
//            val module = SimpleModule()
//            module.addSerializer(SecureHash::class.java, SecureHashSerializer)
//            module.addDeserializer(SecureHash::class.java, SecureHashDeserializer)
//            registerModule(module)
//        }

        const val testRunUniqueId = "1"//UUID.randomUUID()
        const val groupId = "b3de5521-9aef-453f-9f94-62f0d86962a2"
        const val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        const val notaryCpiName = "${TEST_NOTARY_CPI_NAME}_$testRunUniqueId"

        const val aliceX500 = "CN=Alice-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
        const val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
        const val notaryX500 = "CN=Notary-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"

        val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, groupId)
        val bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
        val notaryHoldingId: String = getHoldingIdShortHash(notaryX500, groupId)

        val staticMemberList = listOf(
            aliceX500,
            bobX500,
            notaryX500
        )

        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
        }


        const val tokenType = "com.r3.corda.demo.utxo.contract.CoinState"
        const val currency = "USD"
    }

    private fun convertToTokenBalanceQueryResponseMsg(tokenBalanceQueryResponseMsgStr: String) =
        objectMapper.readValue(
            tokenBalanceQueryResponseMsgStr,
            TokenBalanceQueryResponseMsg::class.java
        )

    private fun convertToTokenClaimResponseMsg(tokenClaimResponseMsgStr: String) =
        objectMapper.readValue(
            tokenClaimResponseMsgStr,
            TokenClaimResponseMsg::class.java
        )

    private fun runTokenBalanceQueryFlow(): TokenBalanceQueryResponseMsg {

        val tokenBalanceQueryFlowName = "com.r3.corda.demo.utxo.TokenBalanceQueryFlow"

        val tokenBalanceQueryRpcStartArgs = mapOf(
            "tokenType" to tokenType,
            "issuerBankX500" to bobX500,
            "currency" to currency
        )

        val flowRequestId = startRpcFlow(aliceHoldingId, tokenBalanceQueryRpcStartArgs, tokenBalanceQueryFlowName)

        val flowResult = awaitRpcFlowFinished(aliceHoldingId, flowRequestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

        return convertToTokenBalanceQueryResponseMsg(flowResult.flowResult!!)
    }

    @Test
    fun `import codesigner certificate`() {
        val retryTimeout = 120.seconds
        val retryInterval = 1.seconds

        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )
            assertWithRetry {
                // Certificate upload can be slow in the combined worker, especially after it has just started up.
                timeout(retryTimeout)
                interval(retryInterval)
                command { importCertificate(CODE_SIGNER_CERT, "code-signer", "cordadev") }
                condition { it.code == 204 }
            }
        }
    }

    @Test
    fun `setup`() {
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
        val notaryActualHoldingId = getOrCreateVirtualNodeFor(notaryX500, notaryCpiName)

        assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        assertThat(notaryActualHoldingId).isEqualTo(notaryHoldingId)

        registerStaticMember(aliceHoldingId)
        registerStaticMember(bobHoldingId)

        registerStaticMember(notaryHoldingId, true)
    }

    @Test
    fun `Token Query Balance - Ensure balance is zero`() {

        val tokenBalanceQuery = runTokenBalanceQueryFlow()

        assertThat(tokenBalanceQuery.balance).isEqualTo(BigDecimal.ZERO)
        assertThat(tokenBalanceQuery.balanceIncludingClaimedTokens).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `Token Query Balance - Create 10 coins`() {
        val rpcStartArgs = mapOf(
            "issuerBankX500" to bobX500,
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
        val tokenBalanceQuery = runTokenBalanceQueryFlow()

        assertThat(tokenBalanceQuery.balance).isEqualTo(BigDecimal.TEN)
        assertThat(tokenBalanceQuery.balanceIncludingClaimedTokens).isEqualTo(BigDecimal.TEN)
    }

    @Test
    fun `Token Query Balance - Claim token`() {
        val rpcStartArgs = mapOf(
            "tokenType" to tokenType,
            "issuerBankX500" to bobX500,
            "currency" to currency,
            "targetAmount" to BigDecimal(5)
        )

        val flowRequestId = startRpcFlow(
            aliceHoldingId,
            rpcStartArgs,
            "com.r3.corda.demo.utxo.TokenClaimQueryFlow"
        )

        val flowResult = awaitRpcFlowFinished(aliceHoldingId, flowRequestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

        val tokenClaimResponseMsg = convertToTokenClaimResponseMsg(flowResult.flowResult!!)
        assertThat(tokenClaimResponseMsg.tokenClaimed).isTrue()
    }

    @Test
    fun `Token Query Balance - Ensure balance is equal to 5 but the balance including claimed tokens is 10`() {
        val tokenBalanceQuery = runTokenBalanceQueryFlow()

        assertThat(tokenBalanceQuery.balance).isEqualTo(BigDecimal(5))
        assertThat(tokenBalanceQuery.balanceIncludingClaimedTokens).isEqualTo(BigDecimal(10))
    }
}

private data class TokenBalanceQueryResponseMsg(
    val balance: BigDecimal,
    val balanceIncludingClaimedTokens: BigDecimal
)

private class TokenClaimResponseMsg(val tokenClaimed: Boolean)
