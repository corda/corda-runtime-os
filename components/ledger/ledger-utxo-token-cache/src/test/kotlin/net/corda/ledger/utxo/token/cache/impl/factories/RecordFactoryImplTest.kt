package net.corda.ledger.utxo.token.cache.impl.factories

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQueryResult
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQueryResult
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimResultStatus
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.internal.TokenBalanceCacheImpl
import net.corda.ledger.utxo.token.cache.factories.RecordFactoryImpl
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.nio.ByteBuffer

class RecordFactoryImplTest {

    private val externalEventResponseFactory = mock<ExternalEventResponseFactory>()
    private val externalEventRequestId = "r1"
    private val flowId = "f1"

    @Test
    fun `create successful claim response`() {
        val avroToken1 = Token()
        val avroToken2 = Token()
        val token1 = mock<CachedToken>().apply { whenever(toAvro()).thenReturn(avroToken1) }
        val token2 = mock<CachedToken>().apply { whenever(toAvro()).thenReturn(avroToken2) }
        val expectedResponse = TokenClaimQueryResult().apply {
            this.poolKey = POOL_CACHE_KEY
            this.claimId = externalEventRequestId
            this.resultType = TokenClaimResultStatus.SUCCESS
            this.claimedTokens = listOf(avroToken1, avroToken2)
        }
        val response = Record<String, FlowEvent>("", "", null)

        whenever(externalEventResponseFactory.success(any(), any(), any())).thenReturn(response)

        val target = RecordFactoryImpl(externalEventResponseFactory)
        val result = target.getSuccessfulClaimResponse(
            flowId,
            externalEventRequestId,
            POOL_KEY,
            listOf(token1, token2)
        )

        assertThat(result).isSameAs(response)
        verify(externalEventResponseFactory).success(externalEventRequestId, flowId, expectedResponse)
    }

    @Test
    fun `create failure claim response`() {
        val expectedResponse = TokenClaimQueryResult().apply {
            this.poolKey = POOL_CACHE_KEY
            this.claimId = externalEventRequestId
            this.resultType = TokenClaimResultStatus.NONE_AVAILABLE
            this.claimedTokens = listOf()
        }
        val response = Record<String, FlowEvent>("", "", null)

        whenever(externalEventResponseFactory.success(any(), any(), any())).thenReturn(response)

        val target = RecordFactoryImpl(externalEventResponseFactory)
        val result = target.getFailedClaimResponse(flowId, externalEventRequestId, POOL_KEY)

        assertThat(result).isSameAs(response)
        verify(externalEventResponseFactory).success(externalEventRequestId, flowId, expectedResponse)
    }

    @Test
    fun `create a token balance response`() {
        val expectedResponse = TokenBalanceQueryResult().apply {
            this.poolKey = POOL_CACHE_KEY
            this.availableBalance = BigDecimal(1.0).toTokenAmount()
            this.totalBalance = BigDecimal(2.0).toTokenAmount()
        }
        val response = Record<String, FlowEvent>("", "", null)

        whenever(externalEventResponseFactory.success(any(), any(), any())).thenReturn(response)

        val target = RecordFactoryImpl(externalEventResponseFactory)
        val result = target.getBalanceResponse(
            flowId,
            externalEventRequestId,
            POOL_KEY,
            TokenBalanceCacheImpl(BigDecimal(1.0), BigDecimal(2.0))
        )

        assertThat(result).isSameAs(response)
        verify(externalEventResponseFactory).success(externalEventRequestId, flowId, expectedResponse)
    }

    private fun BigDecimal.toTokenAmount() = TokenAmount.newBuilder()
        .setScale(scale())
        .setUnscaledValue(ByteBuffer.wrap(unscaledValue().toByteArray()))
        .build()
}
