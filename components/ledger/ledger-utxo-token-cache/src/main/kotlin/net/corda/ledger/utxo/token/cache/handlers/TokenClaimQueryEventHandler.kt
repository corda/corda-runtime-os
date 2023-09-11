package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.services.AvailableTokenService
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import net.corda.messaging.api.records.Record
import java.math.BigDecimal

class TokenClaimQueryEventHandler(
    private val filterStrategy: TokenFilterStrategy,
    private val recordFactory: RecordFactory,
    private val availableTokenService: AvailableTokenService
) : TokenEventHandler<ClaimQuery> {

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: ClaimQuery
    ): Record<String, FlowEvent> {

        // Attempt to select the tokens from the current cache
        var selectionResult = selectTokens(tokenCache, state, event)

        // if we didn't reach the target amount, reload the cache to ensure it's full and retry
        if (selectionResult.first < event.targetAmount) {
            val findResult = availableTokenService.findAvailTokens(event.poolKey, event.ownerHash, event.tagRegex)
            tokenCache.add(findResult.tokens)
            selectionResult = selectTokens(tokenCache, state, event)
        }

        val selectedAmount = selectionResult.first
        val selectedTokens = selectionResult.second

        return if (selectedAmount >= event.targetAmount) {
            state.addNewClaim(event.externalEventRequestId, selectedTokens)
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
