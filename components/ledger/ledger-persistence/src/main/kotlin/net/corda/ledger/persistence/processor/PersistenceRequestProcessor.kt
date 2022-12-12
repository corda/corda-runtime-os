package net.corda.ledger.persistence.processor

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.common.UnsupportedLedgerTypeException
import net.corda.ledger.persistence.common.UnsupportedRequestTypeException
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.utilities.withMDC
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import net.corda.virtualnode.toCorda

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 */
@Suppress("LongParameterList")
class PersistenceRequestProcessor(
    private val entitySandboxService: EntitySandboxService,
    private val delegatedRequestHandlerSelector: DelegatedRequestHandlerSelector,
    private val responseFactory: ResponseFactory
) : DurableProcessor<String, LedgerPersistenceRequest> {

    private companion object {
        val log = contextLogger()
        const val MDC_EXTERNAL_EVENT_ID = "external_event_id"
    }

    override val keyClass = String::class.java

    override val valueClass = LedgerPersistenceRequest::class.java

    override fun onNext(events: List<Record<String, LedgerPersistenceRequest>>): List<Record<*, *>> {
        log.trace { "onNext processing messages ${events.joinToString(",") { it.key }}" }

        return events
            .mapNotNull { it.value }
            .flatMap { request ->
                withMDC(mapOf(MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId)) {
                    try {
                        val holdingIdentity = request.holdingIdentity.toCorda()
                        val sandbox = entitySandboxService.get(holdingIdentity)
                        delegatedRequestHandlerSelector.selectHandler(sandbox, request).execute()
                    } catch (e: Exception) {
                        listOf(
                            when (e) {
                                is UnsupportedLedgerTypeException, is UnsupportedRequestTypeException -> {
                                    responseFactory.fatalErrorResponse(request.flowExternalEventContext, e)
                                }

                                else -> {
                                    responseFactory.errorResponse(request.flowExternalEventContext, e)
                                }
                            }
                        )
                    }
                }
            }
    }
}

