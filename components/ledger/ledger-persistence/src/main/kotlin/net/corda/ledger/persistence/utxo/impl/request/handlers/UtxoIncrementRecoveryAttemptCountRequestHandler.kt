package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.IncrementRecoveryAttemptCount
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record

class UtxoIncrementRecoveryAttemptCountRequestHandler(
    private val incrementRecoveryAttemptCount: IncrementRecoveryAttemptCount,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        persistenceService.incrementRecoveryAttemptCount(incrementRecoveryAttemptCount.id)
        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                EntityResponse(
                    emptyList(),
                    KeyValuePairList(emptyList()),
                    null
                )
            )
        )
    }
}
