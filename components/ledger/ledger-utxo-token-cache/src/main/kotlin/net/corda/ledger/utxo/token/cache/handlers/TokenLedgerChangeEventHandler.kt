package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.LedgerChange
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.messaging.api.records.Record

class TokenLedgerChangeEventHandler : TokenEventHandler<LedgerChange> {

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: LedgerChange
    ): Record<String, FlowEvent>? {
        tokenCache.add(event.producedTokens)

        val consumedStateRefs = event.consumedTokens.map { it.stateRef }.toSet()

        tokenCache.removeAll(consumedStateRefs)
        state.tokensRemovedFromCache(consumedStateRefs)
        return null
    }
}
