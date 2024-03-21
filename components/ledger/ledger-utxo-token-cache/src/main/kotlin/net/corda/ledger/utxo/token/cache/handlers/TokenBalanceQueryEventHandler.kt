package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.BalanceQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.services.AvailableTokenService
import net.corda.messaging.api.records.Record

class TokenBalanceQueryEventHandler(
    private val recordFactory: RecordFactory,
    private val availableTokenService: AvailableTokenService
) : TokenEventHandler<BalanceQuery> {

    override fun handle(
        tokenPoolCache: TokenPoolCache,
        state: PoolCacheState,
        event: BalanceQuery
    ): Record<String, FlowEvent> {
        val tokenBalance = availableTokenService.queryBalance(event.poolKey, event.ownerHash, event.tagRegex, state.claimedTokens())

        return recordFactory.getBalanceResponse(
            event.flowId,
            event.externalEventRequestId,
            event.poolKey,
            tokenBalance
        )
    }
}
