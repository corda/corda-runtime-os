package net.corda.ledger.utxo.token.cache.handlers

import java.math.BigDecimal
import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.BalanceQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.messaging.api.records.Record

class TokenBalanceQueryEventHandler(
    private val recordFactory: RecordFactory,
) : TokenEventHandler<BalanceQuery> {

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: BalanceQuery
    ): Record<String, FlowEvent> {

        // Todo: Calculate balance including claimed tokens. This is similar to balance and balance available. Or balance and balance including pending
        val tokenBalance = calculateTokenBalance(tokenCache, state)

        return recordFactory.getBalanceResponse(
            event.flowId,
            event.externalEventRequestId,
            event.poolKey,
            tokenBalance
        )
    }

    private fun calculateTokenBalance(tokenCache: TokenCache, state: PoolCacheState): BigDecimal {
        var selectedAmount = BigDecimal.ZERO

        tokenCache.filter { !state.isTokenClaimed(it.stateRef) }.forEach {
            selectedAmount += it.amount
        }

        return selectedAmount
    }
}
