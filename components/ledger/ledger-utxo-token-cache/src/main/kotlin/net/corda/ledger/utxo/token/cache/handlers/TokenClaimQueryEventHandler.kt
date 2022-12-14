package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.messaging.api.records.Record
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import java.math.BigDecimal

class TokenClaimQueryEventHandler(
    private val filterStrategy: TokenFilterStrategy,
    private val recordFactory: RecordFactory,
) : TokenEventHandler<ClaimQuery> {

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: ClaimQuery
    ): Record<String, FlowEvent> {

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
}
