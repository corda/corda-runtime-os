package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.v2.FindLedgerTransaction
import net.corda.ledger.common.data.transaction.TransactionStatus.Companion.toTransactionStatus
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.v5.application.serialization.SerializationService

class UtxoFindLedgerTransactionRequestHandler(
    private val findTransaction: FindLedgerTransaction,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        // Find the transaction
        val (transactionContainer, status) = persistenceService.findLedgerTransaction(
            findTransaction.id,
            findTransaction.transactionStatus.toTransactionStatus()
        )

        // return output records
        return listOf(
            utxoOutputRecordFactory.getFindLedgerTransactionSuccessRecord(
                transactionContainer,
                status,
                externalEventContext,
            )
        )
    }
}
