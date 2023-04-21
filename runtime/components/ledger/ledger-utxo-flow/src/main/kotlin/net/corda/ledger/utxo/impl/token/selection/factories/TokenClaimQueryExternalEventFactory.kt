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
import net.corda.schema.Schemas
import net.corda.v5.ledger.utxo.token.selection.TokenClaim
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer

@Component(service = [ExternalEventFactory::class])
class TokenClaimQueryExternalEventFactory @Activate constructor(
    @Reference(service = TokenClaimFactory::class)
    private val tokenClaimFactory: TokenClaimFactory
) : ExternalEventFactory<TokenClaimCriteria, TokenClaimQueryResult, TokenClaim?> {

    override val responseType = TokenClaimQueryResult::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: TokenClaimCriteria
    ): ExternalEventRecord {
        val key = TokenPoolCacheKey().apply {
            this.shortHolderId = checkpoint.holdingIdentity.shortHash.value
            this.tokenType = parameters.tokenType
            this.issuerHash = parameters.issuerHash.toString()
            this.notaryX500Name = parameters.notaryX500Name.toString()
            this.symbol = parameters.symbol
        }

        val claimQuery = TokenClaimQuery().apply {
            this.poolKey = key
            this.requestContext = flowExternalEventContext
            this.ownerHash = parameters.ownerHash?.toString()
            this.tagRegex = parameters.tagRegex
            this.targetAmount = TokenAmount(
                parameters.targetAmount.scale(),
                ByteBuffer.wrap(parameters.targetAmount.unscaledValue().toByteArray())
            )
        }

        return ExternalEventRecord(Schemas.Services.TOKEN_CACHE_EVENT, key, TokenPoolCacheEvent(key, claimQuery))
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: TokenClaimQueryResult): TokenClaim? {
        if (response.resultType == TokenClaimResultStatus.NONE_AVAILABLE) {
            return null
        }

        return tokenClaimFactory.createTokenClaim(
            response.claimId,
            response.poolKey,
            response.claimedTokens.map { tokenClaimFactory.createClaimedToken(response.poolKey, it) }
        )
    }
}
