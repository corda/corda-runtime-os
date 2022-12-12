package net.corda.ledger.utxo.token.cache.factories

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQueryResult
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimReleaseAck
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimResultStatus
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.records.Record
import net.corda.ledger.utxo.token.cache.entities.CachedToken

class RecordFactoryImpl(private val externalEventResponseFactory: ExternalEventResponseFactory) : RecordFactory {

    override fun getSuccessfulClaimResponse(
        flowId: String,
        externalEventRequestId: String,
        poolKey: TokenPoolCacheKey,
        selectedTokens: List<CachedToken>
    ): Record<String, FlowEvent> {
        val payload = TokenClaimQueryResult().apply {
            this.poolKey = poolKey
            this.claimId = externalEventRequestId
            this.resultType = TokenClaimResultStatus.SUCCESS
            this.claimedTokens = selectedTokens.map { it.toAvro() }
        }

        return externalEventResponseFactory.success(externalEventRequestId, flowId, payload)
    }

    override fun getFailedClaimResponse(
        flowId: String,
        externalEventRequestId: String,
        poolKey: TokenPoolCacheKey
    ): Record<String, FlowEvent> {
        val payload = TokenClaimQueryResult().apply {
            this.poolKey = poolKey
            this.resultType = TokenClaimResultStatus.NONE_AVAILABLE
            this.claimedTokens = listOf()
        }

        return externalEventResponseFactory.success(externalEventRequestId, flowId, payload)
    }

    override fun getClaimReleaseAck(
        flowId: String,
        claimId: String
    ): Record<String, FlowEvent> {
        return externalEventResponseFactory.success(claimId, flowId, TokenClaimReleaseAck())
    }
}
