package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.query.execution.VaultNamedQueryExecutor
import net.corda.messaging.api.records.Record

class UtxoExecuteNamedQueryHandler(
    private val externalEventContext: ExternalEventContext,
    private val request: FindWithNamedQuery,
    private val vaultNamedQueryExecutor: VaultNamedQueryExecutor,
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        val response = vaultNamedQueryExecutor.executeQuery(request)
        return listOf(
            externalEventResponseFactory.success(
                externalEventContext,
                response
            )
        )
    }
}
