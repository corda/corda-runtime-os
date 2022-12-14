package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindTransactionRelevantStates
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.v5.application.serialization.SerializationService

class UtxoFindTransactionRelevantStatesRequestHandler(
    private val findTransactionRelevantStates: FindTransactionRelevantStates,
    private val serializationService: SerializationService,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        // Find the relevant states of transaction
        val relevantStates = persistenceService.findTransactionRelevantStates(
            findTransactionRelevantStates.id
        )

        // Return output records
        return listOf(
            utxoOutputRecordFactory.getFindTransactionRelevantStatesSuccessRecord(
                relevantStates,
                externalEventContext,
                serializationService
            )
        )
    }
}
