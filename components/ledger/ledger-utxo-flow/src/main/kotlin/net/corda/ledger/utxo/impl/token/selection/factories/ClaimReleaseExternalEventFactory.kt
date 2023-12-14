package net.corda.ledger.utxo.impl.token.selection.factories

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimRelease
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimReleaseAck
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.impl.token.selection.services.TokenClaimCheckpointService
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ExternalEventFactory::class])
class ClaimReleaseExternalEventFactory @Activate constructor(
    @Reference(service = TokenClaimCheckpointService::class)
    private val tokenClaimCheckpointService: TokenClaimCheckpointService
) :
    ExternalEventFactory<ClaimReleaseParameters, TokenClaimReleaseAck, Unit> {

    override val responseType: Class<TokenClaimReleaseAck> get() = TokenClaimReleaseAck::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: ClaimReleaseParameters
    ): ExternalEventRecord {
        val poolKey = parameters.poolKey.toTokenPoolCacheKey()
        val claimRelease = TokenClaimRelease().apply {
            this.poolKey = poolKey
            this.claimId = parameters.claimId
            this.requestContext = flowExternalEventContext
            this.usedTokenStateRefs = parameters.usedTokens.map { it.toString() }
        }

        tokenClaimCheckpointService.removeClaimFromCheckpoint(checkpoint, parameters.claimId)

        return ExternalEventRecord(
            Schemas.Services.TOKEN_CACHE_EVENT,
            poolKey,
            TokenPoolCacheEvent(poolKey, claimRelease)
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: TokenClaimReleaseAck) {
    }
}
