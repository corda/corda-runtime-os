package net.corda.services.token.handlers

import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.data.services.TokenTimeoutCheck
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.services.token.TokenEventHandler
import net.corda.utilities.time.Clock

class TokenTimeoutCheckEventHandler(
    private val clock: Clock
) : TokenEventHandler<TokenTimeoutCheck> {
    override fun handle(
        key: TokenSetKey,
        state: TokenState,
        event: TokenTimeoutCheck
    ): StateAndEventProcessor.Response<TokenState> {
        state.tokenClaims = state.tokenClaims.filter { it.claimExpiryTime > clock.instant() }
        return StateAndEventProcessor.Response(state, listOf())
    }
}