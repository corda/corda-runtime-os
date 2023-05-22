package net.corda.ledger.utxo.token.cache.handlers

import java.math.BigDecimal
import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.impl.token.selection.impl.TokenBalanceImpl
import net.corda.ledger.utxo.token.cache.entities.BalanceQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import net.corda.messaging.api.records.Record
import net.corda.v5.ledger.utxo.token.selection.TokenBalance

class TokenBalanceQueryEventHandler(
    private val filterStrategy: TokenFilterStrategy,
    private val recordFactory: RecordFactory,
) : TokenEventHandler<BalanceQuery> {

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: BalanceQuery
    ): Record<String, FlowEvent> {

        val tokenBalance = calculateTokenBalance(tokenCache, state, event)

        return recordFactory.getBalanceResponse(
            event.flowId,
            event.externalEventRequestId,
            event.poolKey,
            tokenBalance
        )
    }

    private fun calculateTokenBalance(tokenCache: TokenCache, state: PoolCacheState, event: BalanceQuery): TokenBalance {
        var availableBalance = BigDecimal.ZERO
        var totalBalance = BigDecimal.ZERO

        for (token in filterStrategy.filterTokens(tokenCache, event)) {
            if(!state.isTokenClaimed(token.stateRef) ) {
                availableBalance += token.amount
            }
            totalBalance += token.amount
        }

        return TokenBalanceImpl(availableBalance, totalBalance)
    }
}
