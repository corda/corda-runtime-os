package net.corda.ledger.persistence.processor

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.EntitySandboxService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.toCorda

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 */
@Suppress("LongParameterList")
class PersistenceRequestProcessor(
    private val entitySandboxService: EntitySandboxService,
    private val delegatedRequestHandlerSelector: DelegatedRequestHandlerSelector,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
) : DurableProcessor<String, LedgerPersistenceRequest> {

    private companion object {
        val log = contextLogger()
    }

    override val keyClass = String::class.java

    override val valueClass = LedgerPersistenceRequest::class.java

    override fun onNext(events: List<Record<String, LedgerPersistenceRequest>>): List<Record<*, *>> {
        log.debug { "onNext processing messages ${events.joinToString(",") { it.key }}" }

        return events.mapNotNull { it.value }.flatMap { request ->
            try {
                val holdingIdentity = request.holdingIdentity.toCorda()
                // TODOs calling get on the sandbox could throw a transient exception,
                // we should handle this with the appropriate response type.
                val sandbox = entitySandboxService.get(holdingIdentity)
                delegatedRequestHandlerSelector.selectHandler(sandbox, request).execute()
            } catch (e: Exception) {
                // need to re-add database error handling of transient errors
                log.warn("Unexpected error", e)
                listOf<Record<*, *>>(externalEventResponseFactory.fatalError(request.flowExternalEventContext, e))
            }
        }
    }
}

