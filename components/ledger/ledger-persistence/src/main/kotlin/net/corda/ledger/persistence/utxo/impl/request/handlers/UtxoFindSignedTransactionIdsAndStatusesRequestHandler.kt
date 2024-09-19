package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindTransactionIdsAndStatuses
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.libs.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.messaging.api.records.Record
import net.corda.v5.application.serialization.SerializationService
import java.nio.ByteBuffer

class UtxoFindSignedTransactionIdsAndStatusesRequestHandler(
    private val findTransactions: FindTransactionIdsAndStatuses,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val serializationService: SerializationService
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        val existingTransactions = persistenceService.findSignedTransactionIdsAndStatuses(findTransactions.ids)
        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(
                    listOf(ByteBuffer.wrap(serializationService.serialize(existingTransactions).bytes)),
                    KeyValuePairList(emptyList()),
                    null
                )
            )
        )
    }
}
