package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.services.AvailableTokenService
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class TokenClaimQueryEventHandler(
    private val filterStrategy: TokenFilterStrategy,
    private val recordFactory: RecordFactory,
    private val availableTokenService: AvailableTokenService,
    private val serviceConfiguration: ServiceConfiguration
) : TokenEventHandler<ClaimQuery> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun handle(
        tokenPoolCache: TokenPoolCache,
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

        val tokenCache = tokenPoolCache.get(event.poolKey)

        // Attempt to select the tokens from the current cache
        var selectionResult = selectTokens(tokenCache, state, event)

        // if we didn't reach the target amount, reload the cache to ensure it's full and retry
        if (selectionResult.first < event.targetAmount) {
            // The max. number of tokens retrieved should be the configured size plus the number of claimed tokens
            // This way the cache size will be equal to the configured size once the claimed tokens are removed
            // from the query results
            val maxTokens = serviceConfiguration.cachedTokenPageSize + state.claimedTokens().size
            val findResult = availableTokenService.findAvailTokens(event.poolKey, event.ownerHash, event.tagRegex, maxTokens)

            // Remove the claimed tokens from the query results
            val tokens = findResult.tokens.filterNot { state.isTokenClaimed(it.stateRef) }

            // Replace the tokens in the cache with the ones from the query result that have not been claimed
            tokenCache.add(tokens)
            // Update the token pool cache so the expiry period is refreshed
            tokenPoolCache.put(event.poolKey, tokenCache)

            selectionResult = selectTokens(tokenCache, state, event)
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
