package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistFilteredTransaction
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory

class UtxoPersistFilteredTransactionRequestHandler
@Suppress("LongParameterList")
constructor(
    private val request: PersistFilteredTransaction,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : RequestHandler {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): List<Record<*, *>> {

        persistenceService.persistFilteredTransaction(
            request.transactionId,
            request.privacySalt.array(),
            request.account,
            request.transactionStatus,
            request.metadataHash
        )

        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(emptyList(), KeyValuePairList(emptyList()), null) // TODO what should we return?
            )
        )
    }
}