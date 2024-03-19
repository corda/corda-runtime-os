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
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.SimpleTokenFilterStrategy
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import net.corda.messaging.api.records.Record
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
import java.lang.Thread.sleep
import java.math.BigDecimal

class TokenClaimQueryEventHandlerTest {

    private val recordFactory: RecordFactory = mock()
    private val availableTokenService: AvailableTokenService = mock()
    private val tokenCache: TokenCache = mock()
    private val filterStrategy = mock<TokenFilterStrategy>()
    private val serviceConfiguration = mock<ServiceConfiguration>()
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
        whenever(serviceConfiguration.tokenCacheExpiryPeriodMilliseconds).doAnswer { 200 }
    }

    @Test
    fun `empty cache should return non found`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfiguration)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getFailedClaimResponse(flowId, claimId, POOL_KEY)
    }

    @Test
    fun `when non found no claim should be created`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfiguration)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(poolCacheState, never()).addNewClaim(any(), any())
    }

    @Test
    fun `when non found no claim should be created1`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfiguration)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(poolCacheState, never()).addNewClaim(any(), any())
    }

    @Test
    fun `when tokens selected a claim should be created`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfiguration)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), any(), any(), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))
        cachedTokens += token101

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(poolCacheState).addNewClaim(claimId, listOf(token101))
    }

    @Test
    fun `query for tokens finds none when sum of available tokens is less than target`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, mock())
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))
        cachedTokens += token99

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getFailedClaimResponse(flowId, claimId, POOL_KEY)
    }

    @Test
    fun `ensure the cache expiry period avoids multiple calls to the db in a short period of time`() {
        // Make the expiry period long enough so the second call does not go to the database
        val serviceConfigurationLongExpiryPeriod = mock<ServiceConfiguration>() {
            whenever(it.tokenCacheExpiryPeriodMilliseconds).doAnswer { 30000 }
        }

        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfigurationLongExpiryPeriod)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))

        // There are no tokens available so the handle has always to go to the database

        // First call go to the database
        target.handle(tokenCache, poolCacheState, claimQuery)

        // Second call. Can't go to the database because of the expiry period
        target.handle(tokenCache, poolCacheState, claimQuery)

        // Ensure the database call was made only once
        verify(availableTokenService, times(1)).findAvailTokens(any(), eq(null), eq(null), any())
    }

    @Test
    fun `ensure the cache expiry period is respected`() {
        val tokenCacheExpiryPeriodMilliseconds = 1000L

        // Make the expiry period long enough so the second call does not go to the database
        val serviceConfigurationLongExpiryPeriod = mock<ServiceConfiguration>() {
            whenever(it.tokenCacheExpiryPeriodMilliseconds).doAnswer { tokenCacheExpiryPeriodMilliseconds }
        }

        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfigurationLongExpiryPeriod)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))

        // There are no tokens available so the handle has always to go to the database

        // First call go to the database
        target.handle(tokenCache, poolCacheState, claimQuery)

        // Second call. Can't go to the database because of the expiry period
        target.handle(tokenCache, poolCacheState, claimQuery)

        sleep(tokenCacheExpiryPeriodMilliseconds)

        // Third call. Go to the database because the cached has been invalidated
        target.handle(tokenCache, poolCacheState, claimQuery)

        // Ensure the database call was made twice
        verify(availableTokenService, times(2)).findAvailTokens(any(), eq(null), eq(null), any())
    }

    @Test
    fun `ensure the handler always calls the db`() {
        val tokenCacheExpiryPeriodMilliseconds = 0L

        // Make the expiry period long enough so the second call does not go to the database
        val serviceConfigurationLongExpiryPeriod = mock<ServiceConfiguration>() {
            whenever(it.tokenCacheExpiryPeriodMilliseconds).doAnswer { tokenCacheExpiryPeriodMilliseconds }
        }

        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfigurationLongExpiryPeriod)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))

        // There are no tokens available so the handle has always to go to the database

        // First call go to the database
        target.handle(tokenCache, poolCacheState, claimQuery)

        // Second call. Go to the database
        target.handle(tokenCache, poolCacheState, claimQuery)

        // Third call. Go to the database
        target.handle(tokenCache, poolCacheState, claimQuery)

        // Ensure the database call was made twice
        verify(availableTokenService, times(3)).findAvailTokens(any(), eq(null), eq(null), any())
    }

    @Test
    fun `query for tokens with exact amount should claim token`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfiguration)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), any(), any(), any()))
            .thenReturn(AvailTokenQueryResult(claimQuery.poolKey, emptySet()))
        cachedTokens += token100

        val result = target.handle(tokenCache, poolCacheState, claimQuery)

        assertThat(result).isSameAs(claimQueryResult)
        verify(recordFactory).getSuccessfulClaimResponse(flowId, claimId, POOL_KEY, listOf(token100))
    }

    @Test
    fun `query for tokens should select multiple to reach target amount`() {
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfiguration)
        val claimQuery = createClaimQuery(110)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), any(), any(), any()))
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
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfiguration)
        val claimQuery = createClaimQuery(100)
        whenever(recordFactory.getFailedClaimResponse(any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), eq(null), eq(null), any()))
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
        val target = TokenClaimQueryEventHandler(filterStrategy, recordFactory, availableTokenService, serviceConfiguration)
        val claimQuery = createClaimQuery(110)
        whenever(recordFactory.getSuccessfulClaimResponse(any(), any(), any(), any())).thenReturn(claimQueryResult)
        whenever(availableTokenService.findAvailTokens(any(), any(), any(), any()))
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
            TokenClaimQueryEventHandler(SimpleTokenFilterStrategy(), recordFactory, availableTokenService, serviceConfiguration)
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

    private fun createClaimQuery(targetAmount: Int, tag: String? = null, ownerHash: String? = null): ClaimQuery {
        return ClaimQuery(claimId, flowId, BigDecimal(targetAmount), tag, ownerHash, POOL_KEY)
    }
}
