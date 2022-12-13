package net.corda.ledger.utxo.token.cache.impl.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.messaging.api.records.Record
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimQueryEventHandler
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class TokenClaimQueryEventHandlerTest {

    private val recordFactory: RecordFactory = mock()
    private val tokenCache: TokenCache = mock()
    private val filterStrategy = mock<TokenFilterStrategy>()
    private val poolCacheState: PoolCacheState = mock()

    private val token99Ref = "r1"
    private val token99 = mock<CachedToken>().apply {
        whenever(amount).thenReturn(BigDecimal(99))
        whenever(stateRef).thenReturn(token99Ref)
    }
    private val token100Ref = "r2"
    private val token100 = mock<CachedToken>().apply {
        whenever(amount).thenReturn(BigDecimal(100))
        whenever(stateRef).thenReturn(token100Ref)
    }
    private val token101Ref = "r3"
    private val token101 = mock<CachedToken>().apply {
        whenever(amount).thenReturn(BigDecimal(101))
        whenever(stateRef).thenReturn(token101Ref)
    }

    private val cachedTokens = mutableListOf<CachedToken>()
    private val claimQueryResult = Record<String, FlowEvent>("", "", null)
    private val claimId = "r1"
    private val flowId = "f1"

    @BeforeEach
    fun setup() {
        whenever(filterStrategy.filterTokens(any(), any())).doAnswer { cachedTokens.toList() }
    }

    @Test
    fun `empty cache should return non found`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getFailedClaimResponse(flowId, claimId, POOL_CACHE_KEY)
    }

    @Test
    fun `when non found no claim should be created`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(poolCacheState, never()).addNewClaim(any(), any())
    }

    @Test
    fun `when tokens selected a claim should be created`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        cachedTokens += token101

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(poolCacheState).addNewClaim(claimId, listOf(token101))
    }

    @Test
    fun `query for tokens finds none when sum of available tokens is less than target`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        cachedTokens += token99

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getFailedClaimResponse(flowId, claimId, POOL_CACHE_KEY)
    }

    @Test
    fun `query for tokens with exact amount should claim token`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        cachedTokens += token100

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getSuccessfulClaimResponse(flowId, claimId, POOL_CACHE_KEY, listOf(token100))
    }

    @Test
    fun `query for tokens should select multiple to reach target amount`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory)
        val claimQuery = createClaimQuery(110)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        cachedTokens += token99
        cachedTokens += token100
        cachedTokens += token101

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getSuccessfulClaimResponse(
            flowId,
            claimId,
            POOL_CACHE_KEY,
            listOf(token99, token100)
        )
    }

    @Test
    fun `query for tokens should return none when claimed tokens stop target being reached`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(poolCacheState.isTokenClaimed(token100Ref)).thenReturn(true)
        whenever(poolCacheState.isTokenClaimed(token101Ref)).thenReturn(true)
        cachedTokens += token99
        cachedTokens += token100
        cachedTokens += token101

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getFailedClaimResponse(flowId, claimId, POOL_CACHE_KEY)
    }

    @Test
    fun `query for tokens should not include tokens already claimed`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory)
        val claimQuery = createClaimQuery(110)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        whenever(poolCacheState.isTokenClaimed(token100Ref)).thenReturn(true)
        cachedTokens += token99
        cachedTokens += token100
        cachedTokens += token101

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getSuccessfulClaimResponse(
            flowId, claimId,
            POOL_CACHE_KEY,
            listOf(token99, token101)
        )
    }

    private fun createClaimQuery(targetAmount: Int): ClaimQuery {
        return ClaimQuery(claimId, flowId, BigDecimal(targetAmount), "", "", POOL_CACHE_KEY)
    }
}

