package net.corda.ledger.utxo.token.cache.impl.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.data.TokenClaim
import net.corda.ledger.utxo.token.cache.entities.AvailTokenQueryResult
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimQueryEventHandler
import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.ledger.utxo.token.cache.services.AvailableTokenService
import net.corda.ledger.utxo.token.cache.services.SimpleTokenFilterStrategy
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import net.corda.ledger.utxo.token.cache.services.internal.BackoffManagerImpl
import net.corda.messaging.api.records.Record
import net.corda.test.util.time.AutoTickTestClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class TokenClaimQueryEventHandlerTest {

    private val recordFactory: RecordFactory = mock()
    private val availableTokenService: AvailableTokenService = mock()
    private val tokenCache: TokenCache = mock()
    private val filterStrategy = mock<TokenFilterStrategy>()
    private val poolCacheState: PoolCacheState = mock()

    private val token99Ref = "r1"
    private val token99 = mock<CachedToken>().apply {
        whenever(amount).thenReturn(BigDecimal(99))
        whenever(stateRef).thenReturn(token99Ref)
        whenever(tag).thenReturn(null)
        whenever(ownerHash).thenReturn(null)
    }
    private val token100Ref = "r2"
    private val token100 = mock<CachedToken>().apply {
        whenever(amount).thenReturn(BigDecimal(100))
        whenever(stateRef).thenReturn(token100Ref)
        whenever(tag).thenReturn(null)
        whenever(ownerHash).thenReturn(null)
    }
    private val token101Ref = "r3"
    private val token101 = mock<CachedToken>().apply {
        whenever(amount).thenReturn(BigDecimal(101))
        whenever(stateRef).thenReturn(token101Ref)
        whenever(tag).thenReturn(null)
        whenever(ownerHash).thenReturn(null)
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
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock(), mock())
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any(), eq(null)))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getFailedClaimResponse(flowId, claimId, POOL_KEY)
    }

    @Test
    fun `when non found no claim should be created`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock(), mock())
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any(), eq(null)))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(poolCacheState, never()).addNewClaim(any(), any())
    }

    @Test
    fun `when tokens selected a claim should be created`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock(), mock())
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), any(), any(), any(), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))
        cachedTokens += token101

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(poolCacheState).addNewClaim(claimId, listOf(token101))
    }

    @Test
    fun `query for tokens finds none when sum of available tokens is less than target`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock(), mock())
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any(), eq(null)))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))
        cachedTokens += token99

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getFailedClaimResponse(flowId, claimId, POOL_KEY)
    }

    @Test
    fun `query for tokens with exact amount should claim token`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock(), mock())
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), any(), any(), any(), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))
        cachedTokens += token100

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getSuccessfulClaimResponse(flowId, claimId, POOL_KEY, listOf(token100))
    }

    @Test
    fun `query for tokens should select multiple to reach target amount`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock(), mock())
        val claimQuery = createClaimQuery(110)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), any(), any(), any(), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))
        cachedTokens += token99
        cachedTokens += token100
        cachedTokens += token101

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getSuccessfulClaimResponse(flowId, claimId, POOL_KEY, listOf(token99, token100))
    }

    @Test
    fun `query for tokens should return none when claimed tokens stop target being reached`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock(), mock())
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any(), eq(null)))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))
        whenever(poolCacheState.isTokenClaimed(token100Ref)).thenReturn(true)
        whenever(poolCacheState.isTokenClaimed(token101Ref)).thenReturn(true)
        cachedTokens += token99
        cachedTokens += token100
        cachedTokens += token101

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getFailedClaimResponse(flowId, claimId, POOL_KEY)
    }

    @Test
    fun `query for tokens should not include tokens already claimed`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock(), mock())
        val claimQuery = createClaimQuery(110)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), any(), any(), any(), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))
        whenever(poolCacheState.isTokenClaimed(token100Ref)).thenReturn(true)
        cachedTokens += token99
        cachedTokens += token100
        cachedTokens += token101

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getSuccessfulClaimResponse(
            flowId,
            claimId,
            POOL_KEY,
            listOf(token99, token101)
        )
    }

    @Test
    fun `ensure the same token set is returned if a claim request is processed more than once`() {
        val target =
            TokenClaimQueryEventHandler(SimpleTokenFilterStrategy(), recordFactory, availableTokenService, mock(), mock())
        val claimQuery = createClaimQuery(100, null, null)
        val tokenClaim = TokenClaim.newBuilder().setClaimId(claimQuery.externalEventRequestId).build()
        whenever(recordFactory.getSuccessfulClaimResponseWithListTokens(any(), any(), any(), any())).thenReturn(claimQueryResult)
        whenever(poolCacheState.claim(claimQuery.externalEventRequestId)).thenReturn(tokenClaim)

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getSuccessfulClaimResponseWithListTokens(
            flowId,
            claimId,
            POOL_KEY,
            tokenClaim.claimedTokens
        )
    }

    @Test
    fun `ensure only one request will trigger a db call when there are insufficient tokens`() {
        val backoffManager = BackoffManagerImpl(
            AutoTickTestClock(Instant.EPOCH, Duration.ofSeconds(1)),
            Duration.ofMillis(10000L),
            Duration.ofMillis(10000L)
        )
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock(), backoffManager)
        val claimQuery = createClaimQuery(100)
        whenever(
            availableTokenService.findAvailTokens(any(), eq(null), eq(null), any(), eq(null))
        ).thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))

        target.handle(tokenCache, poolCacheState, claimQuery) // Accesses the db
        target.handle(tokenCache, poolCacheState, claimQuery) // No access to the db
        target.handle(tokenCache, poolCacheState, claimQuery) // No access to the db

        verify(availableTokenService, times(1)).findAvailTokens(any(), eq(null), eq(null), any(), eq(null))
    }

    @Test
    fun `ensure a db request is triggered after the backoff time expires`() {
        val clock = AutoTickTestClock(Instant.EPOCH, Duration.ofSeconds(1))
        val backoffManager = BackoffManagerImpl(
            clock,
            Duration.ofMillis(1000L),
            Duration.ofMillis(10000L)
        )
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock(), backoffManager)
        val claimQuery = createClaimQuery(100)
        whenever(
            availableTokenService.findAvailTokens(any(), eq(null), eq(null), any(), eq(null))
        ).thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))

        target.handle(tokenCache, poolCacheState, claimQuery) // Accesses the db - Expiry period 1 second
        target.handle(tokenCache, poolCacheState, claimQuery) // No access to the db - Expiry period 2 seconds
        clock.instant() // Advance time
        clock.instant() // Advance time
        target.handle(tokenCache, poolCacheState, claimQuery) // Accesses access to the db

        verify(availableTokenService, times(2)).findAvailTokens(any(), eq(null), eq(null), any(), eq(null))
    }

    private fun createClaimQuery(targetAmount: Int, tag: String? = null, ownerHash: String? = null): ClaimQuery {
        return ClaimQuery(claimId, flowId, BigDecimal(targetAmount), tag, ownerHash, POOL_KEY, null)
    }
}
