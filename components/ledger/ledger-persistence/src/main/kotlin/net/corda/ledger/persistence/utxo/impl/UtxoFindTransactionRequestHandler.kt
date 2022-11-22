package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.util.contextLogger

class UtxoFindTransactionRequestHandler(
    private val findTransaction: FindTransaction,
    private val serializationService: SerializationService,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : RequestHandler {

    private companion object {
        val log = contextLogger()
    }

    override fun execute(): List<Record<*, *>> {
        // Find the transaction
        val transactionContainer = persistenceService.findTransaction(findTransaction.id)

        // return output records
        return listOf(
            utxoOutputRecordFactory.getFindTransactionSuccessRecord(
                transactionContainer,
                externalEventContext,
                serializationService
            )
        )
    }
}
