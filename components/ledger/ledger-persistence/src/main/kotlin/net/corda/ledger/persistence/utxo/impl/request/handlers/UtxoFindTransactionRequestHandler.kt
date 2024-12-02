package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.ledger.common.data.transaction.TransactionStatus.Companion.toTransactionStatus
import net.corda.ledger.libs.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.messaging.api.records.Record

class UtxoFindTransactionRequestHandler(
    private val findTransaction: FindTransaction,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        val (transactionContainer, status) = persistenceService.findSignedTransaction(
            findTransaction.id,
            findTransaction.transactionStatus.toTransactionStatus()
        )

        return listOf(
            utxoOutputRecordFactory.getFindTransactionSuccessRecord(
                transactionContainer,
                status,
                externalEventContext,
            )
        )
    }
}
