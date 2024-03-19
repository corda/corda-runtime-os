package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.services.AvailableTokenService
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant

class TokenClaimQueryEventHandler(
    private val filterStrategy: TokenFilterStrategy,
    private val recordFactory: RecordFactory,
    private val availableTokenService: AvailableTokenService,
    private val serviceConfiguration: ServiceConfiguration,
) : TokenEventHandler<ClaimQuery> {

    private var tokenCacheExpiryTime = Instant.now()
    private val tokenCacheEnabled = serviceConfiguration.tokenCacheExpiryPeriodMilliseconds >= 0

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    init {
        logger.info("Token cache expiry period: ${serviceConfiguration.tokenCacheExpiryPeriodMilliseconds} ms")
        logger.info("Token cache enabled: $tokenCacheEnabled")
    }

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: ClaimQuery
    ): Record<String, FlowEvent> {
        val claimId = event.externalEventRequestId
        val claim = state.claim(claimId)
        if (claim != null) {
            logger.warn("A token claim is being processed more than once. ClaimId: $claimId")

            return recordFactory.getSuccessfulClaimResponseWithListTokens(
                event.flowId,
                event.externalEventRequestId,
                event.poolKey,
                claim.claimedTokens
            )
        }

        val selectionResult = if (tokenCacheEnabled) {
            selectTokenWithCacheEnabled(tokenCache, state, event)
        } else {
            selectTokenWithCacheDisabled(tokenCache, state, event)
        }

        val selectedAmount = selectionResult.first
        val selectedTokens = selectionResult.second

        return if (selectedAmount >= event.targetAmount) {
            // Claimed tokens should not be stored in the token cache
            tokenCache.removeAll(selectedTokens.map { it.stateRef }.toSet())
            state.addNewClaim(claimId, selectedTokens)
            recordFactory.getSuccessfulClaimResponse(
                event.flowId,
                event.externalEventRequestId,
                event.poolKey,
                selectedTokens
            )
        } else {
            recordFactory.getFailedClaimResponse(event.flowId, event.externalEventRequestId, event.poolKey)
        }
    }

    private fun updateCache(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: ClaimQuery
    ) {
        // The max. number of tokens retrieved should be the configured size plus the number of claimed tokens
        // This way the cache size will be equal to the configured size once the claimed tokens are removed
        // from the query results
        val maxTokens = serviceConfiguration.cachedTokenPageSize + state.claimedTokens().size
        val findResult =
            availableTokenService.findAvailTokens(event.poolKey, event.ownerHash, event.tagRegex, maxTokens)

        // Remove the claimed tokens from the query results
        val tokens = findResult.tokens.filterNot { state.isTokenClaimed(it.stateRef) }

        // Replace the tokens in the cache with the ones from the query result that have not been claimed
        tokenCache.add(tokens)
    }

    private fun selectTokenWithCacheEnabled(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: ClaimQuery
    ): Pair<BigDecimal, List<CachedToken>> {
        // Attempt to select the tokens from the current cache
        var selectionResult = selectTokens(tokenCache, state, event)

        // if we didn't reach the target amount, reload the cache to ensure it's full and retry.
        // But only if the cache has not been recently reloaded.
        if (selectionResult.first < event.targetAmount) {
            val currentTime = Instant.now()
            if (tokenCacheExpiryTime < currentTime) {
                tokenCacheExpiryTime = currentTime.plusMillis(serviceConfiguration.tokenCacheExpiryPeriodMilliseconds)
                // The cache is only updated periodically when required. This is to avoid going to often to the database
                // which can degrade performance. For instance, when there are too few tokens available.
                updateCache(tokenCache, state, event)
                selectionResult = selectTokens(tokenCache, state, event)
            } else {
                logger.warn("Some tokens might not be accessible. Token cache expiry time: $tokenCacheExpiryTime")
            }
        }

        return selectionResult
    }

    // Call this method with caution. This is only for scenarios when going to the database is unavoidable. This
    // method can easily degrade performance.
    private fun selectTokenWithCacheDisabled(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: ClaimQuery
    ): Pair<BigDecimal, List<CachedToken>> {
        // Update the cache regardless. There are use cases when going to the database is mandatory.
        // For instance, when tokens with short expiry dates are continuously being generated.
        updateCache(tokenCache, state, event)
        return selectTokens(tokenCache, state, event)
    }

    private fun selectTokens(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: ClaimQuery
    ): Pair<BigDecimal, List<CachedToken>> {
        val selectedTokens = mutableListOf<CachedToken>()
        var selectedAmount = BigDecimal.ZERO

        for (token in filterStrategy.filterTokens(tokenCache, event)) {
            if (selectedAmount >= event.targetAmount) {
                break
            }

            if (state.isTokenClaimed(token.stateRef)) {
                continue
            }

            selectedAmount += token.amount
            selectedTokens += token
        }

        return selectedAmount to selectedTokens
    }
}
