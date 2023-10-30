package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindBatchTransactions
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.common.data.transaction.TransactionStatus.Companion.toTransactionStatus
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.v5.application.serialization.SerializationService
import java.nio.ByteBuffer

class UtxoFindBatchTransactionsRequestHandler(
    private val findBatchTransactions: FindBatchTransactions,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val serializationService: SerializationService
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        val transactionContainers = persistenceService.findBatchSignedTransactions(
            findBatchTransactions.ids,
            findBatchTransactions.transactionStatus.toTransactionStatus()
        )

        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(
                    listOf(ByteBuffer.wrap(serializationService.serialize(transactionContainers).bytes)),
                    KeyValuePairList(emptyList()),
                    null
                )
            )
        )
    }
}