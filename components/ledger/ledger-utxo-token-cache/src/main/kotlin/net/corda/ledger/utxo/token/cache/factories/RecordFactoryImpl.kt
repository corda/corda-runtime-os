package net.corda.ledger.utxo.token.cache.factories

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQueryResult
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQueryResult
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimReleaseAck
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimResultStatus
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.messaging.api.records.Record
import java.math.BigDecimal
import java.nio.ByteBuffer
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.v5.ledger.utxo.token.selection.TokenBalance

class RecordFactoryImpl(private val externalEventResponseFactory: ExternalEventResponseFactory) : RecordFactory {

    override fun getSuccessfulClaimResponse(
        flowId: String,
        externalEventRequestId: String,
        poolKey: TokenPoolKey,
        selectedTokens: List<CachedToken>
    ): Record<String, FlowEvent> {
        return getSuccessfulClaimResponseWithListTokens(flowId, externalEventRequestId, poolKey, selectedTokens.map { it.toAvro() })
    }

    override fun getSuccessfulClaimResponseWithListTokens(
        flowId: String,
        externalEventRequestId: String,
        poolKey: TokenPoolKey,
        selectedTokens: List<Token>
    ): Record<String, FlowEvent> {
        val payload = TokenClaimQueryResult().apply {
            this.poolKey = poolKey.toAvro()
            this.claimId = externalEventRequestId
            this.resultType = TokenClaimResultStatus.SUCCESS
            this.claimedTokens = selectedTokens
        }

        return externalEventResponseFactory.success(externalEventRequestId, flowId, payload)
    }

    override fun getFailedClaimResponse(
        flowId: String,
        externalEventRequestId: String,
        poolKey: TokenPoolKey
    ): Record<String, FlowEvent> {
        val payload = TokenClaimQueryResult().apply {
            this.poolKey = poolKey.toAvro()
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
        poolKey: TokenPoolKey,
        tokenBalance: TokenBalance
    ): Record<String, FlowEvent> {
        val payload = TokenBalanceQueryResult().apply {
            this.poolKey = poolKey.toAvro()
            this.availableBalance = tokenBalance.availableBalance.toTokenAmount()
            this.totalBalance = tokenBalance.totalBalance.toTokenAmount()
        }

        return externalEventResponseFactory.success(externalEventRequestId, flowId, payload)
    }

    private fun BigDecimal.toTokenAmount() =
        TokenAmount.newBuilder()
            .setScale(this.scale())
            .setUnscaledValue(ByteBuffer.wrap(this.unscaledValue().toByteArray()))
            .build()

}
