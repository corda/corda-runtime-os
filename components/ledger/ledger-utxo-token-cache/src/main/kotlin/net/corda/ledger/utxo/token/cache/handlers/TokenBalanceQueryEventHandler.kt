package net.corda.ledger.utxo.token.cache.handlers

import java.math.BigDecimal
import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.impl.token.selection.impl.TokenBalanceImpl
import net.corda.ledger.utxo.token.cache.entities.BalanceQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.messaging.api.records.Record
import net.corda.v5.ledger.utxo.token.selection.TokenBalance

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

    private fun calculateTokenBalance(tokenCache: TokenCache, state: PoolCacheState): TokenBalance {
        var balance = BigDecimal.ZERO
        var balanceIncludingClaimedTokens = BigDecimal.ZERO

        tokenCache.forEach {
            if(!state.isTokenClaimed(it.stateRef) ) {
                balance += it.amount
            }
            balanceIncludingClaimedTokens += it.amount
        }

        return TokenBalanceImpl(balance, balanceIncludingClaimedTokens)
    }
}
