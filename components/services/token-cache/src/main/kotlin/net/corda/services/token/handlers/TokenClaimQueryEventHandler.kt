package net.corda.services.token.handlers

import net.corda.data.services.Token
import net.corda.data.services.TokenClaim
import net.corda.data.services.TokenClaimQuery
import net.corda.data.services.TokenClaimResult
import net.corda.data.services.TokenClaimResultStatus
import net.corda.data.services.TokenClaimState
import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.services.token.TokenCacheConfiguration
import net.corda.services.token.TokenEventHandler
import net.corda.services.token.TokenRecordFactory
import net.corda.utilities.time.Clock
import java.time.Instant

class TokenClaimQueryEventHandler(
    private val clock: Clock,
    private val tokenRecordFactory: TokenRecordFactory,
    private val tokenCacheConfiguration: TokenCacheConfiguration
) : TokenEventHandler<TokenClaimQuery> {
    override fun handle(
        key: TokenSetKey,
        state: TokenState,
        event: TokenClaimQuery
    ): StateAndEventProcessor.Response<TokenState> {
        // For the PoC we implement a very basic select all tokens until target reached
        // this is fine for v1, but this will be replaced with a more sophisticated implementation.
        val lockedTokens = state.tokenClaims.flatMap { it.claimedTokens }.toSet()

        val selectedTokens = mutableListOf<Token>()
        var unclaimedRemaining = event.targetAmount
        var withClaimedRemaining = event.targetAmount
        for (availableToken in state.availableTokens) {
            if (unclaimedRemaining <= 0) {
                break
            }

            withClaimedRemaining -= availableToken.amount

            if (!lockedTokens.contains(availableToken.stateRef)) {
                unclaimedRemaining -= availableToken.amount
                selectedTokens.add(availableToken)
            }
        }

        val claimResultStatus = if (unclaimedRemaining <= 0) {
            TokenClaimResultStatus.SUCCESS
        } else if (withClaimedRemaining <= 0) {
            TokenClaimResultStatus.AVAILABLE_CLAIMED
        } else {
            selectedTokens.clear()
            TokenClaimResultStatus.NONE_AVAILABLE
        }

        val expiryTime = getExpiryTime()
        val claim = TokenClaim().apply {
            this.tokenSetKey = key
            this.claimExpiryTime = expiryTime
            this.claimedTokens = selectedTokens
        }

        if (claimResultStatus != TokenClaimResultStatus.NONE_AVAILABLE) {
            val claimState = TokenClaimState().apply {
                this.claimedTokens = selectedTokens.map { it.stateRef }
                this.claimRequestId = event.requestContext.requestId
                this.claimExpiryTime = expiryTime
            }

            state.tokenClaims = state.tokenClaims + listOf(claimState)
        }

        val tokenClaimResult = TokenClaimResult().apply {
            this.requestContext = event.requestContext
            this.status = claimResultStatus
            this.tokenClaim = claim
        }

        return StateAndEventProcessor.Response(
            state,
            listOf(tokenRecordFactory.getQueryClaimResponse(tokenClaimResult, clock.instant()))
        )
    }

    private fun getExpiryTime(): Instant {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli() + tokenCacheConfiguration.tokenClaimTimeout)
    }
}