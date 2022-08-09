package net.corda.services.token.handlers

import net.corda.data.services.TokenLedgerEvent
import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.services.token.TokenEventHandler

class TokenLedgerEventHandler : TokenEventHandler<TokenLedgerEvent> {
    override fun handle(
        key: TokenSetKey,
        state: TokenState,
        event: TokenLedgerEvent
    ): StateAndEventProcessor.Response<TokenState> {
        val toRemove = event.consumedTokens.map { it.stateRef }.toSet()
        state.availableTokens = state.availableTokens
            .filterNot { toRemove.contains(it.stateRef) } + event.producedTokens

        return StateAndEventProcessor.Response(state, listOf())
    }
}

