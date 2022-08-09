package net.corda.services.token.handlers

import net.corda.data.services.TokenClaimRelease
import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.services.token.TokenEventHandler
import net.corda.v5.base.util.contextLogger

class TokenClaimReleaseEventHandler : TokenEventHandler<TokenClaimRelease> {

    private companion object {
        val log = contextLogger()
    }

    override fun handle(
        key: TokenSetKey,
        state: TokenState,
        event: TokenClaimRelease
    ): StateAndEventProcessor.Response<TokenState> {
        val existingClaim = state.tokenClaims.firstOrNull { it.claimRequestId == event.requestContext.requestId }
        if (existingClaim == null) {
            log.info(
                "Claim Release request could not find existing claim for requestId='${event.requestContext.requestId}'")
            return StateAndEventProcessor.Response(state, listOf())
        }

        val tokensToRemove = (event.releasedTokenRefs + event.usedTokenRefs).toSet()

        existingClaim.claimedTokens = existingClaim.claimedTokens.filterNot { tokensToRemove.contains(it) }

        if (!existingClaim.claimedTokens.any()) {
            state.tokenClaims = state.tokenClaims.filterNot { it.claimRequestId == event.requestContext.requestId }
        }

        val consumedTokens = event.usedTokenRefs.toSet()
        state.availableTokens = state.availableTokens.filterNot { consumedTokens.contains(it.stateRef) }

        return StateAndEventProcessor.Response(state, listOf())
    }
}