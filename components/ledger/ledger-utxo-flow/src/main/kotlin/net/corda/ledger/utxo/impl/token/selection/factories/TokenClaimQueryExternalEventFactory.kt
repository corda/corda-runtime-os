package net.corda.ledger.utxo.impl.token.selection.factories

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQueryResult
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimResultStatus
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.token.query.TokenClaimCriteriaParameters
import net.corda.ledger.utxo.impl.token.selection.services.TokenClaimCheckpointService
import net.corda.schema.Schemas
import net.corda.v5.ledger.utxo.token.selection.Strategy
import net.corda.v5.ledger.utxo.token.selection.TokenClaim
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer

@Component(service = [ExternalEventFactory::class])
class TokenClaimQueryExternalEventFactory @Activate constructor(
    @Reference(service = TokenClaimFactory::class)
    private val tokenClaimFactory: TokenClaimFactory,
    @Reference(service = TokenClaimCheckpointService::class)
    private val tokenClaimCheckpointService: TokenClaimCheckpointService
) : ExternalEventFactory<TokenClaimCriteriaParameters, TokenClaimQueryResult, TokenClaim?> {

    override val responseType = TokenClaimQueryResult::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: TokenClaimCriteriaParameters
    ): ExternalEventRecord {
        val criteria = parameters.tokenClaimCriteria
        val key = TokenPoolCacheKey().apply {
            this.shortHolderId = checkpoint.holdingIdentity.shortHash.value
            this.tokenType = criteria.tokenType
            this.issuerHash = criteria.issuerHash.toString()
            this.notaryX500Name = criteria.notaryX500Name.toString()
            this.symbol = criteria.symbol
        }

        val claimQuery = TokenClaimQuery().apply {
            this.poolKey = key
            this.requestContext = flowExternalEventContext
            this.ownerHash = criteria.ownerHash?.toString()
            this.tagRegex = criteria.tagRegex
            this.targetAmount = TokenAmount(
                criteria.targetAmount.scale(),
                ByteBuffer.wrap(criteria.targetAmount.unscaledValue().toByteArray())
            )
            this.strategy = parameters.tokenClaimCriteria.strategy?.toAvro()
        }

        return ExternalEventRecord(Schemas.Services.TOKEN_CACHE_EVENT, key, TokenPoolCacheEvent(key, claimQuery))
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: TokenClaimQueryResult): TokenClaim? {
        if (response.resultType == TokenClaimResultStatus.NONE_AVAILABLE) {
            return null
        }

        tokenClaimCheckpointService.addClaimToCheckpoint(checkpoint, response.claimId, response.poolKey)

        return tokenClaimFactory.createTokenClaim(
            response.claimId,
            response.poolKey,
            response.claimedTokens.map { tokenClaimFactory.createClaimedToken(response.poolKey, it) }
        )
    }

    private fun Strategy.toAvro() = when (this) {
        Strategy.RANDOM -> net.corda.data.ledger.utxo.token.selection.data.Strategy.RANDOM
        Strategy.PRIORITY -> net.corda.data.ledger.utxo.token.selection.data.Strategy.PRIORITY
    }
}
