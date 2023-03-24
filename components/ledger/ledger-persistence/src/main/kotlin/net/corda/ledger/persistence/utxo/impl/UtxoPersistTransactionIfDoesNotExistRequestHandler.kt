package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.messaging.api.records.Record
import net.corda.v5.application.serialization.SerializationService
import java.nio.ByteBuffer

class UtxoPersistTransactionIfDoesNotExistRequestHandler(
    private val transaction: UtxoTransactionReader,
    private val externalEventContext: ExternalEventContext,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val serializationService: SerializationService,
    private val persistenceService: UtxoPersistenceService
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        // persist the transaction if it doesn't exist
        val result = persistenceService.persistTransactionIfDoesNotExist(transaction)

        // should this do token related side effect things?
        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(listOf(ByteBuffer.wrap(serializationService.serialize(result).bytes)))
            )
        )
    }
}
