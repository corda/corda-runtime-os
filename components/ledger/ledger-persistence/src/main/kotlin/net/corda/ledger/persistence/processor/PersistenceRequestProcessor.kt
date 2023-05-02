package net.corda.ledger.persistence.processor

import net.corda.crypto.core.parseSecureHash
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.flow.utils.toMap
import net.corda.ledger.persistence.common.InconsistentLedgerStateException
import net.corda.ledger.persistence.common.UnsupportedLedgerTypeException
import net.corda.ledger.persistence.common.UnsupportedRequestTypeException
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.MDC_EXTERNAL_EVENT_ID
import net.corda.utilities.trace
import net.corda.utilities.withMDC
import net.corda.v5.application.flows.FlowContextPropertyKeys.CPK_FILE_CHECKSUM
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

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
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass = String::class.java

    override val valueClass = LedgerPersistenceRequest::class.java

    override fun onNext(events: List<Record<String, LedgerPersistenceRequest>>): List<Record<*, *>> {
        log.trace { "onNext processing messages ${events.joinToString(",") { it.key }}" }

        return events
            .mapNotNull { it.value }
            .flatMap { request ->
                val clientRequestId = request.flowExternalEventContext.contextProperties.toMap()[MDC_CLIENT_ID] ?: ""

                withMDC(
                    mapOf(
                        MDC_CLIENT_ID to clientRequestId,
                        MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId
                    )
                ) {
                    try {
                        val holdingIdentity = request.holdingIdentity.toCorda()
                        val cpkFileHashes = request.flowExternalEventContext.contextProperties.items
                            .filter { it.key.startsWith(CPK_FILE_CHECKSUM) }
                            .map { it.value.toSecureHash() }
                            .toSet()

                        val sandbox = entitySandboxService.get(holdingIdentity, cpkFileHashes)
                        delegatedRequestHandlerSelector.selectHandler(sandbox, request).execute()
                    } catch (e: Exception) {
                        listOf(
                            when (e) {
                                is UnsupportedLedgerTypeException,
                                is UnsupportedRequestTypeException,
                                is InconsistentLedgerStateException -> {
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

    private fun String.toSecureHash() = parseSecureHash(this)
}

