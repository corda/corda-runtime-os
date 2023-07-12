package net.corda.ledger.utxo.token.cache.handlers

import java.math.BigDecimal
import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.BalanceQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import net.corda.messaging.api.records.Record

class TokenBalanceQueryEventHandler(
    private val filterStrategy: TokenFilterStrategy,
    private val recordFactory: RecordFactory,
) : TokenEventHandler<BalanceQuery> {

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: BalanceQuery
    ): Record<String, FlowEvent> {

        val availableTotalBalancePair = calculateTokenBalance(tokenCache, state, event)

        return recordFactory.getBalanceResponse(
            event.flowId,
            event.externalEventRequestId,
            event.poolKey,
            availableTotalBalancePair
        )
    }

    private fun calculateTokenBalance(tokenCache: TokenCache, state: PoolCacheState, event: BalanceQuery): Pair<BigDecimal, BigDecimal> {
        var availableBalance = BigDecimal.ZERO
        var totalBalance = BigDecimal.ZERO

        for (token in filterStrategy.filterTokens(tokenCache, event)) {
            if(!state.isTokenClaimed(token.stateRef) ) {
                availableBalance += token.amount
            }
            totalBalance += token.amount
        }

        return Pair(availableBalance, totalBalance)
    }
}
