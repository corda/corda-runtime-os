package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindSignedLedgerTransaction
import net.corda.ledger.common.data.transaction.TransactionStatus.Companion.toTransactionStatus
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.libs.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record

class UtxoFindSignedLedgerTransactionRequestHandler(
    private val findTransaction: FindSignedLedgerTransaction,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        val (transactionContainer, status) = persistenceService.findSignedLedgerTransaction(
            findTransaction.id,
            findTransaction.transactionStatus.toTransactionStatus()
        )
        return listOf(
            utxoOutputRecordFactory.getFindSignedLedgerTransactionSuccessRecord(
                transactionContainer,
                status,
                externalEventContext,
            )
        )
    }
}
