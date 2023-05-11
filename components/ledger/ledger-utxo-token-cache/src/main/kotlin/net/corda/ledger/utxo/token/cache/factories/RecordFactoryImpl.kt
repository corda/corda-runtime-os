package net.corda.ledger.utxo.token.cache.factories

import java.math.BigDecimal
import java.nio.ByteBuffer
import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQueryResult
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQueryResult
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimReleaseAck
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimResultStatus
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.messaging.api.records.Record
import net.corda.v5.ledger.utxo.token.selection.TokenBalance

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
            this.claimId = externalEventRequestId
            this.resultType = TokenClaimResultStatus.NONE_AVAILABLE
            this.claimedTokens = listOf()
        }

        return externalEventResponseFactory.success(externalEventRequestId, flowId, payload)
    }

    override fun getClaimReleaseAck(
        flowId: String,
        externalEventRequestId: String
    ): Record<String, FlowEvent> {
        return externalEventResponseFactory.success(externalEventRequestId, flowId, TokenClaimReleaseAck())
    }

    override fun getBalanceResponse(
        flowId: String,
        externalEventRequestId: String,
        poolKey: TokenPoolCacheKey,
        tokenBalance: TokenBalance
    ): Record<String, FlowEvent> {
        val payload = TokenBalanceQueryResult().apply {
            this.poolKey = poolKey
            this.balance = tokenBalance.balance.toTokenAmount()
            this.balanceIncludingClaimedTokens = tokenBalance.balanceIncludingClaimedTokens.toTokenAmount()
        }

        return externalEventResponseFactory.success(externalEventRequestId, flowId, payload)
    }

    private fun BigDecimal.toTokenAmount() =
        TokenAmount.newBuilder()
            .setScale(this.scale())
            .setUnscaledValue(ByteBuffer.wrap(this.unscaledValue().toByteArray()))
            .build()

}
