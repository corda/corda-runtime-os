package net.corda.ledger.utxo.token.selection.impl.factories

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQueryResult
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.impl.token.selection.factories.TokenBalanceQueryExternalEventFactory
import net.corda.ledger.utxo.impl.token.selection.impl.ALICE_X500_HOLDING_ID
import net.corda.ledger.utxo.impl.token.selection.impl.BOB_X500_NAME
import net.corda.ledger.utxo.impl.token.selection.impl.TokenBalanceImpl
import net.corda.ledger.utxo.impl.token.selection.impl.toSecureHash
import net.corda.schema.Schemas.Services.TOKEN_CACHE_EVENT
import net.corda.v5.ledger.utxo.token.selection.TokenBalanceCriteria
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.nio.ByteBuffer

class TokenBalanceQueryExternalEventFactoryTest {
    private val tokenType = "tt"
    private val symbol = "s"
    private val issuerHash = "issuer".toSecureHash()
    private val notaryX500Name = BOB_X500_NAME
    private val key = TokenPoolCacheKey.newBuilder()
        .setShortHolderId(ALICE_X500_HOLDING_ID.shortHash.value)
        .setTokenType(tokenType)
        .setIssuerHash(issuerHash.toString())
        .setNotaryX500Name(notaryX500Name.toString())
        .setSymbol(symbol)
        .build()

    private val checkpoint = mock<FlowCheckpoint>().apply {
        whenever(holdingIdentity).thenReturn(ALICE_X500_HOLDING_ID)
    }

    @Test
    fun `createExternalEvent should return balance query event record`() {
        val flowExternalEventContext = ExternalEventContext("r1", "f1", KeyValuePairList())

        val parameters = TokenBalanceCriteria(tokenType, issuerHash, notaryX500Name, symbol)

        val expectedBalanceQuery = TokenBalanceQuery().apply {
            this.poolKey = key
            this.requestContext = flowExternalEventContext
        }

        val expectedExternalEventRecord = ExternalEventRecord(
            TOKEN_CACHE_EVENT,
            key,
            TokenPoolCacheEvent(key, expectedBalanceQuery)
        )

        val result = TokenBalanceQueryExternalEventFactory().createExternalEvent(
            checkpoint,
            flowExternalEventContext,
            parameters
        )

        assertThat(result).isEqualTo(expectedExternalEventRecord)
    }

    @Test
    fun `resumeWith returns the balance of the token pool`() {
        val expectedTokenBalance = TokenBalanceImpl(BigDecimal(1.0), BigDecimal(2.0))

        val response = TokenBalanceQueryResult().apply {
            this.poolKey = key
            this.availableBalance = expectedTokenBalance.availableBalance.toTokenAmount()
            this.totalBalance = expectedTokenBalance.totalBalance.toTokenAmount()
        }

        val result = TokenBalanceQueryExternalEventFactory().resumeWith(checkpoint, response)

        assertThat(result.availableBalance).isEqualTo(expectedTokenBalance.availableBalance)
        assertThat(result.totalBalance).isEqualTo(expectedTokenBalance.totalBalance)
    }

    private fun BigDecimal.toTokenAmount() = TokenAmount.newBuilder()
        .setScale(scale())
        .setUnscaledValue(ByteBuffer.wrap(unscaledValue().toByteArray()))
        .build()
}
