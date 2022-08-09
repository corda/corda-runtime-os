package net.corda.flow.application.services

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.services.TokenClaimQuery
import net.corda.data.services.TokenClaimResult
import net.corda.data.services.TokenClaimResultStatus
import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenSetKey
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.handlers.events.ExternalEventRequest
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.v5.application.services.ClaimCriteria
import net.corda.v5.application.services.ClaimedToken
import net.corda.v5.application.services.ClaimedTokens
import net.corda.v5.application.services.ClaimedTokensResultType
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [ExternalEventRequest.Handler::class])
class TokenClaimQueryExternalEventHandler @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : ExternalEventRequest.Handler<ClaimCriteriaRequest, TokenClaimResult, ClaimedTokens> {

    private companion object {
        val log = contextLogger()
    }

    override fun suspending(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: ClaimCriteriaRequest
    ): ExternalEventRequest.EventRecord {

        log.debug("Suspending for a token claim query '${parameters}'")

        val key = getTokenSetKey(checkpoint, parameters.criteria)
        return ExternalEventRequest.EventRecord(
            Schemas.Services.TOKEN_CACHE_EVENT,
            key,
            TokenEvent().apply {
                tokenSetKey = key
                payload = getTokenClaimQuery(
                    checkpoint,
                    flowExternalEventContext,
                    parameters.criteria,
                    parameters.awaitWhenClaimed,
                    parameters.awaitExpiryTime
                )
            }
        )
    }

    override fun resuming(
        checkpoint: FlowCheckpoint,
        response: ExternalEventRequest.Response<TokenClaimResult>
    ): ClaimedTokens {
        log.debug("resuming from a token claim query '${response}'")
        val claimResponse = response.lastResponsePayload
        return ClaimedTokensImpl(
            flowFiberService,
            claimResponse.requestContext.requestId,
            claimResponse.getTokens(),
            claimResponse.getResultType()
        )
    }

    private fun TokenClaimResult.getTokens(): List<ClaimedToken> {
        val key = tokenClaim.tokenSetKey
        return tokenClaim.claimedTokens.map {
            ClaimedTokenImpl(
                it.stateRef,
                key.tokenType,
                key.issuerHash,
                key.notaryHash,
                key.symbol,
                it.tag,
                it.ownerHash,
                it.amount
            )
        }.toList()
    }

    private fun TokenClaimResult.getResultType(): ClaimedTokensResultType {
        return when (this.status) {
            TokenClaimResultStatus.SUCCESS -> ClaimedTokensResultType.SUCCESS
            TokenClaimResultStatus.NONE_AVAILABLE -> ClaimedTokensResultType.NONE_AVAILABLE
            TokenClaimResultStatus.AVAILABLE_CLAIMED -> ClaimedTokensResultType.AVAILABLE_CLAIMED
            else -> throw IllegalStateException("The status '${this.status}' is not recognised.")
        }
    }

    private fun getTokenSetKey(flowCheckpoint: FlowCheckpoint, criteria: ClaimCriteria): TokenSetKey {
        return TokenSetKey().apply {
            this.shortHolderId = flowCheckpoint.holdingIdentity.shortHash
            this.tokenType = criteria.tokenType
            this.issuerHash = criteria.issuerHash
            this.notaryHash = criteria.notaryHash
            this.symbol = criteria.symbol
        }
    }

    private fun getTokenClaimQuery(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        criteria: ClaimCriteria,
        awaitWhenClaimed: Boolean = false,
        awaitExpiryTime: Instant? = null
    ): TokenClaimQuery {
        return TokenClaimQuery().apply {
            requestContext = flowExternalEventContext
            tokenSet = getTokenSetKey(checkpoint, criteria)
            ownerHash = criteria.ownerHash
            tagRegex = criteria.tagRegex
            targetAmount = criteria.targetAmount
            this.awaitWhenClaimed = awaitWhenClaimed
            this.awaitExpiryTime = awaitExpiryTime
        }
    }
}