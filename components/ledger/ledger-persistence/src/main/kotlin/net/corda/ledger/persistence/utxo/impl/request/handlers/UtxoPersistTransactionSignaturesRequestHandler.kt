package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistTransactionSignatures
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record

class UtxoPersistTransactionSignaturesRequestHandler(
    private val persistTransactionSignatures: PersistTransactionSignatures,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        persistenceService.persistTransactionSignatures(
            persistTransactionSignatures.id,
            persistTransactionSignatures.signatures.map { it.array() },
            persistTransactionSignatures.startingIndex
        )

        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(emptyList(), KeyValuePairList(emptyList()), null)
            )
        )
    }
}
