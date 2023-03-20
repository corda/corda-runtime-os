package net.corda.ledger.persistence.query.execution.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryRequest
import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.query.execution.VaultNamedQueryExecutor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record

/**
 * Processes messages received from the uniqueness check topic, and responds using the external
 * events response API.
 */
class VaultNamedQueryProcessor(
    private val vaultNamedQueryExecutor: VaultNamedQueryExecutor,
    private val externalEventResponseFactory: ExternalEventResponseFactory
): DurableProcessor<String, ExecuteVaultNamedQueryRequest> {

    override val keyClass = String::class.java
    override val valueClass = ExecuteVaultNamedQueryRequest::class.java

    override fun onNext(events: List<Record<String, ExecuteVaultNamedQueryRequest>>): List<Record<*, *>> {
        val requests = events.mapNotNull { it.value }

        return requests.map { request ->
            try {
                val resultSet = vaultNamedQueryExecutor.executeQuery(request)
                externalEventResponseFactory.success(
                    request.flowExternalEventContext,
                    ExecuteVaultNamedQueryResponse(
                        resultSet.results,
                        resultSet.newOffset,
                        resultSet.hasNextPage()
                    )
                )
            } catch (e: Exception) {
                // TODO better error handling?
                externalEventResponseFactory.platformError(
                    request.flowExternalEventContext,
                    ExceptionEnvelope(e::class.java.canonicalName, e.message)
                )
            }
        }
    }
}