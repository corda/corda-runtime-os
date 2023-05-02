package net.corda.ledger.utxo.token.cache.impl.handlers

import java.math.BigDecimal
import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.impl.token.selection.impl.TokenBalanceImpl
import net.corda.ledger.utxo.token.cache.entities.BalanceQuery
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.handlers.TokenBalanceQueryEventHandler
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TokenBalanceQueryEventHandlerTest {

    private val recordFactory: RecordFactory = mock()
    private val tokenCache: TokenCache = mock()
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

    private val cachedTokens = mutableListOf<CachedToken>()
    private val balanceQueryResult = Record<String, FlowEvent>("", "", null)
    private val balanceId = "r1"
    private val flowId = "f1"

    @BeforeEach
    fun setup() {
        whenever(tokenCache.iterator()).doAnswer { cachedTokens.iterator() }
    }

    @Test
    fun `empty cache should return a balance equal to zero`() {
        val target = TokenBalanceQueryEventHandler(recordFactory)
        val balanceQuery = createBalanceQuery()
        whenever(recordFactory.getBalanceResponse(any(), any(), any(), any())).thenReturn(balanceQueryResult)

        val result = target.handle(tokenCache, poolCacheState, balanceQuery)

        assertThat(result).isSameAs(balanceQueryResult)
        verify(recordFactory).getBalanceResponse(
            flowId,
            balanceId,
            POOL_CACHE_KEY,
            TokenBalanceImpl(BigDecimal(0.0), BigDecimal(0.0))
        )
    }

    @Test
    fun `the correct balance is calculated - balance and balanceIncludingClaimedTokens are the same`() {
        val target = TokenBalanceQueryEventHandler(recordFactory)
        val balanceQuery = createBalanceQuery()
        whenever(recordFactory.getBalanceResponse(any(), any(), any(), any())).thenReturn(balanceQueryResult)
        cachedTokens += token99

        val result = target.handle(tokenCache, poolCacheState, balanceQuery)

        assertThat(result).isSameAs(balanceQueryResult)
        verify(recordFactory).getBalanceResponse(
            flowId,
            balanceId,
            POOL_CACHE_KEY,
            TokenBalanceImpl(BigDecimal(99), BigDecimal(99))
        )
    }

    @Test
    fun `the correct balance is calculated - balance and balanceIncludingClaimedTokens are different`() {
        val target = TokenBalanceQueryEventHandler(recordFactory)
        val balanceQuery = createBalanceQuery()
        whenever(recordFactory.getBalanceResponse(any(), any(), any(), any())).thenReturn(balanceQueryResult)
        cachedTokens += token99
        cachedTokens += token100

        val poolCacheState: PoolCacheState = mock {
            whenever(it.isTokenClaimed(eq(token100Ref))).thenReturn(true)
        }

        val result = target.handle(tokenCache, poolCacheState, balanceQuery)

        assertThat(result).isSameAs(balanceQueryResult)
        verify(recordFactory).getBalanceResponse(
            flowId,
            balanceId,
            POOL_CACHE_KEY,
            TokenBalanceImpl(BigDecimal(99), BigDecimal(199))
        )
    }

    private fun createBalanceQuery(): BalanceQuery {
        return BalanceQuery(balanceId, flowId, POOL_CACHE_KEY)
    }
}

