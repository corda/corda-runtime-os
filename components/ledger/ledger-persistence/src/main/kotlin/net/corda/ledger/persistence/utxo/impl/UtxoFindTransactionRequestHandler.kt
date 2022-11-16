package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.persistence.EntityResponse
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.ResponseFactory
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.util.contextLogger
import java.nio.ByteBuffer

class UtxoFindTransactionRequestHandler(
    private val findTransaction: FindTransaction,
    private val serializationService: SerializationService,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val responseFactory: ResponseFactory
) : RequestHandler {

    private companion object {
        val log = contextLogger()
    }

    override fun execute(): List<Record<*, *>> {
        // Find the transaction
        val transactionContainer = persistenceService.findTransaction(findTransaction.id)

        val findTransactionSuccessRecord = responseFactory.successResponse(
            externalEventContext,
            EntityResponse(
                listOfNotNull(transactionContainer)
                    .map { ByteBuffer.wrap(serializationService.serialize(it).bytes) }
            )
        )

        // return output records
        return listOf(
            findTransactionSuccessRecord
        )
    }
}
