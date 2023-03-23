package net.corda.ledger.persistence.utxo.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.identity.HoldingIdentity
import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryRequest
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.query.execution.VaultNamedQueryExecutor
import net.corda.messaging.api.records.Record

class UtxoExecuteCustomQueryHandler(
    private val externalEventContext: ExternalEventContext,
    private val holdingIdentity: HoldingIdentity,
    private val request: ExecuteVaultNamedQueryRequest,
    private val vaultNamedQueryExecutor: VaultNamedQueryExecutor,
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {

        return try {
            val response = vaultNamedQueryExecutor.executeQuery(holdingIdentity, request)
            listOf(externalEventResponseFactory.success(
                externalEventContext,
                response
            ))
        } catch (e: Exception) {
            listOf(externalEventResponseFactory.platformError(
                externalEventContext,
                ExceptionEnvelope(e::class.java.canonicalName, e.message)
            ))
        }
    }
}
