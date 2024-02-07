package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.crypto.core.SecureHashImpl
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindFilteredTransactionsAndSignatures
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.v5.ledger.utxo.StateRef

class UtxoFindFilteredTransactionsAndSignaturesRequestHandler(
    private val findFilteredTransactionsAndSignatures: FindFilteredTransactionsAndSignatures,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : RequestHandler {
    override fun execute(): List<Record<*, *>> {
        val filteredTransactionFetchResults = persistenceService.findFilteredTransactionsAndSignatures(
            findFilteredTransactionsAndSignatures.stateRefs.map {
                StateRef(
                    SecureHashImpl(it.transactionId.algorithm, it.transactionId.bytes.array()),
                    it.index
                )
            }
        )
        return listOf(
            utxoOutputRecordFactory.getFindFilteredTransactionsAndSignaturesSuccessRecord(
                filteredTransactionFetchResults,
                externalEventContext
            )
        )
    }
}
