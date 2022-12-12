package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.persistence.EntityResponse
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.ResponseFactory
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.observer.UtxoToken
import java.nio.ByteBuffer

@Suppress("Unused")
class UtxoOutputRecordFactoryImpl(
    private val responseFactory: ResponseFactory
) : UtxoOutputRecordFactory {

    override fun getTokenCacheChangeEventRecords(
        producedTokens: List<UtxoToken>,
        consumedTokens: List<UtxoToken>
    ): List<Record<TokenPoolCacheKey, TokenPoolCacheEvent>> {
        // TODO("Not yet implemented")
        return emptyList()
    }

    override fun getFindTransactionSuccessRecord(
        transactionContainer: SignedTransactionContainer?,
        externalEventContext: ExternalEventContext,
        serializationService: SerializationService
    ): Record<String, FlowEvent> {
        return responseFactory.successResponse(
            externalEventContext,
            EntityResponse(
                listOfNotNull(transactionContainer)
                    .map { ByteBuffer.wrap(serializationService.serialize(it).bytes) }
            )
        )
    }

    override fun getPersistTransactionSuccessRecord(
        externalEventContext: ExternalEventContext
    ): Record<String, FlowEvent> {
        return responseFactory.successResponse(
            externalEventContext,
            EntityResponse(emptyList())
        )
    }
}
