package net.corda.ledger.utxo.token.cache.handlers

import java.math.BigDecimal
import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.Helper.toDto
import net.corda.ledger.utxo.token.cache.entities.BalanceQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenBalance
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.services.AvailableTokenService
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import net.corda.messaging.api.records.Record

class TokenBalanceQueryEventHandler(
    private val filterStrategy: TokenFilterStrategy,
    private val recordFactory: RecordFactory,
    private val availableTokenService: AvailableTokenService
) : TokenEventHandler<BalanceQuery> {

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: BalanceQuery
    ): Record<String, FlowEvent> {

        val tokenBalance = calculateTokenBalance(tokenCache, state, event)

        val tokenBalance1 = availableTokenService.queryBalance(event.poolKey.toDto(), event.ownerHash, event.tagRegex, state.claimedTokens())

        println(tokenBalance1)

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

        return TokenBalance(availableBalance, totalBalance)
    }
}
