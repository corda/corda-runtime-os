package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.records.Record
import net.corda.ledger.utxo.token.cache.entities.LedgerChange
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache

class TokenLedgerChangeEventHandler(
    private val externalEventResponseFactory: ExternalEventResponseFactory,
) : TokenEventHandler<LedgerChange> {

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: LedgerChange
    ): Record<String, FlowEvent>? {
        tokenCache.add(event.producedTokens)

        val consumedStateRefs = event.consumedTokens.map { it.stateRef }.toSet()

        tokenCache.removeAll(consumedStateRefs)
        state.tokensRemovedFromCache(consumedStateRefs)

        // HACK: Added for testing will be removed by CORE-5722 (ledger integration)
        return event.flowId?.let { externalEventResponseFactory.success(event.claimId!!, event.flowId, "HACK") }
    }
}
