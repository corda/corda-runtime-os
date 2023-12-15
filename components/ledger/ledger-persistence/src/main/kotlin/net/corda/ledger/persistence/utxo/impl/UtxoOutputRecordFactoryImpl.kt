package net.corda.ledger.persistence.utxo.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.UtxoTransactionOutput
import net.corda.data.ledger.persistence.UtxoTransactionOutputs
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.persistence.EntityResponse
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.ResponseFactory
import net.corda.schema.Schemas.Services.TOKEN_CACHE_EVENT
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.virtualnode.HoldingIdentity
import java.nio.ByteBuffer

class UtxoOutputRecordFactoryImpl(
    private val responseFactory: ResponseFactory,
    private val serializationService: SerializationService
) : UtxoOutputRecordFactory {

    override fun getTokenCacheChangeEventRecords(
        holdingIdentity: HoldingIdentity,
        producedTokens: List<Pair<StateAndRef<*>, UtxoToken>>,
        consumedTokens: List<Pair<StateAndRef<*>, UtxoToken>>
    ): List<Record<TokenPoolCacheKey, TokenPoolCacheEvent>> {
        val groupedProducedTokens = producedTokens.groupBy { createPoolKeyRecord(holdingIdentity, it) }
        val groupedConsumedTokens = consumedTokens.groupBy { createPoolKeyRecord(holdingIdentity, it) }
        val uniqueTokenPoolKeys = (groupedProducedTokens.keys + groupedConsumedTokens.keys).toSet()

        return uniqueTokenPoolKeys.map { poolKey ->
            val producedTokenRecords = groupedProducedTokens[poolKey]?.map(::createTokenRecord) ?: emptyList()
            val consumedTokenRecords = groupedConsumedTokens[poolKey]?.map(::createTokenRecord) ?: emptyList()
            TokenPoolCacheEvent.newBuilder()
                .setPoolKey(poolKey)
                .setPayload(
                    TokenLedgerChange.newBuilder()
                        .setPoolKey(poolKey)
                        .setProducedTokens(producedTokenRecords)
                        .setConsumedTokens(consumedTokenRecords)
                        .build()
                )
                .build()
        }.map { tokenPoolCacheEvent ->
            Record(
                topic = TOKEN_CACHE_EVENT,
                key = tokenPoolCacheEvent.poolKey,
                value = tokenPoolCacheEvent
            )
        }
    }

    override fun getFindTransactionSuccessRecord(
        transactionContainer: SignedTransactionContainer?,
        status: String?,
        externalEventContext: ExternalEventContext,
    ): Record<String, FlowEvent> {
        return responseFactory.successResponse(
            externalEventContext,
            EntityResponse(
                listOf(transactionContainer to status).map { ByteBuffer.wrap(serializationService.serialize(it).bytes) },
                KeyValuePairList(emptyList()),
                null
            )
        )
    }

    override fun getFindSignedLedgerTransactionSuccessRecord(
        transactionContainer: SignedLedgerTransactionContainer?,
        status: String?,
        externalEventContext: ExternalEventContext,
    ): Record<String, FlowEvent> {
        return responseFactory.successResponse(
            externalEventContext,
            EntityResponse(
                listOf(transactionContainer to status).map { ByteBuffer.wrap(serializationService.serialize(it).bytes) },
                KeyValuePairList(emptyList()),
                null
            )
        )
    }

    override fun getStatesSuccessRecord(
        states: List<UtxoVisibleTransactionOutputDto>,
        externalEventContext: ExternalEventContext,
    ): Record<String, FlowEvent> {
        return responseFactory.successResponse(
            externalEventContext,
            UtxoTransactionOutputs(
                states.map { state ->
                    UtxoTransactionOutput.newBuilder()
                        .setTransactionId(state.transactionId)
                        .setIndex(state.leafIndex)
                        .setInfo(ByteBuffer.wrap(state.info))
                        .setData(ByteBuffer.wrap(state.data))
                        .build()
                }
            )
        )
    }

    override fun getPersistTransactionSuccessRecord(
        externalEventContext: ExternalEventContext
    ): Record<String, FlowEvent> {
        return responseFactory.successResponse(
            externalEventContext,
            EntityResponse(emptyList(), KeyValuePairList(emptyList()), null),
        )
    }

    private fun createPoolKeyRecord(holdingIdentity: HoldingIdentity, stateTokenPair: Pair<StateAndRef<*>, UtxoToken>): TokenPoolCacheKey {
        val (stateAndRef, token) = stateTokenPair
        return TokenPoolCacheKey.newBuilder()
            .setShortHolderId(holdingIdentity.shortHash.value)
            .setTokenType(token.poolKey.tokenType)
            .setIssuerHash(token.poolKey.issuerHash.toString())
            .setNotaryX500Name(stateAndRef.state.notaryName.toString())
            .setSymbol(token.poolKey.symbol)
            .build()
    }

    private fun createTokenRecord(stateTokenPair: Pair<StateAndRef<*>, UtxoToken>): Token {
        val (stateAndRef, token) = stateTokenPair
        return Token.newBuilder()
            .setStateRef(stateAndRef.ref.toString())
            .setAmount(
                TokenAmount.newBuilder()
                    .setScale(token.amount.scale())
                    .setUnscaledValue(ByteBuffer.wrap(token.amount.unscaledValue().toByteArray()))
                    .build()
            )
            .setOwnerHash(token.filterFields?.ownerHash?.toString())
            .setTag(token.filterFields?.tag)
            .build()
    }
}
