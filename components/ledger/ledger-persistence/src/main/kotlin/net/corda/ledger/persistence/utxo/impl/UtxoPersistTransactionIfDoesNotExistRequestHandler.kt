package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistTransactionIfDoesNotExist
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.common.data.transaction.TransactionStatus.Companion.toTransactionStatus
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.exceptions.NullParameterException
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.util.contextLogger
import java.nio.ByteBuffer

class UtxoPersistTransactionIfDoesNotExistRequestHandler(
    private val request: PersistTransactionIfDoesNotExist,
    private val externalEventContext: ExternalEventContext,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val serializationService: SerializationService,
    private val persistenceService: UtxoPersistenceService
) : RequestHandler {

    private companion object {
        const val CORDA_ACCOUNT = "corda.account"
        val log = contextLogger()
    }

    override fun execute(): List<Record<*, *>> {
        val account = externalEventContext.contextProperties.items.find { it.key == CORDA_ACCOUNT }?.value
            ?: throw NullParameterException("Flow external event context property '$CORDA_ACCOUNT' not set")

        val result = persistenceService.persistTransactionIfDoesNotExist(
            serializationService.deserialize(request.transaction.array()),
            request.status.toTransactionStatus(),
            account
        )

        // should this do token related side effect things?
        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(listOf(ByteBuffer.wrap(serializationService.serialize(result).bytes)))
            )
        )
    }
}
