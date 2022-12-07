package net.corda.ledger.persistence.consensual.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.data.persistence.EntityResponse
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.consensual.ConsensualPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.ResponseFactory
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.util.contextLogger
import java.nio.ByteBuffer

class ConsensualPersistTransactionRequestHandler(
    private val persistTransaction: PersistTransaction,
    private val serializationService: SerializationService,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: ConsensualPersistenceService,
    private val responseFactory: ResponseFactory
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        // persist the transaction
        val transaction = ConsensualTransactionReaderImpl(serializationService, externalEventContext, persistTransaction)
        val missingCpks = persistenceService.persistTransaction(transaction)

        // return output records
        return listOf(
            responseFactory.successResponse(
                externalEventContext,
                EntityResponse(
                    missingCpks.map { ByteBuffer.wrap(serializationService.serialize(it).bytes) }
                )
            )
        )
    }
}
