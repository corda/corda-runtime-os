package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.libs.utxo.UtxoPersistenceService
import net.corda.ledger.libs.utxo.UtxoTransactionReader
import net.corda.messaging.api.records.Record

class UtxoPersistTransactionIfDoesNotExistRequestHandler(
    private val transaction: UtxoTransactionReader,
    private val externalEventContext: ExternalEventContext,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory,
    private val persistenceService: UtxoPersistenceService
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        // persist the transaction if it doesn't exist
        val result = persistenceService.persistTransactionIfDoesNotExist(transaction)

        // should this do token related side effect things?
        return listOf(
            utxoOutputRecordFactory.getPersistTransactionIfDoesNotExistSuccessRecord(result, externalEventContext)
        )
    }
}
