package net.corda.ledger.verification.processor.impl

import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.verification.processor.ResponseFactory
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.ledger.verification.sanbox.VerificationSandboxService
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.withMDC
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 */
@Suppress("LongParameterList")
class VerificationRequestProcessor(
    private val verificationSandboxService: VerificationSandboxService,
    private val requestHandler: VerificationRequestHandler,
    private val responseFactory: ResponseFactory
) : DurableProcessor<String, TransactionVerificationRequest> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MDC_EXTERNAL_EVENT_ID = "external_event_id"
    }

    override val keyClass = String::class.java

    override val valueClass = TransactionVerificationRequest::class.java

    override fun onNext(events: List<Record<String, TransactionVerificationRequest>>): List<Record<*, *>> {
        log.trace { "onNext processing messages ${events.joinToString(",") { it.key }}" }

        return events
            .mapNotNull { it.value }
            .map { request ->
                withMDC(mapOf(MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId)) {
                    try {
                        val holdingIdentity = request.holdingIdentity.toCorda()
                        val cpkIds = request.cpkMetadata.mapTo(mutableSetOf()) { SecureHash.parse(it.fileChecksum) }
                        val sandbox = verificationSandboxService.get(holdingIdentity, cpkIds)
                        requestHandler.handleRequest(sandbox, request)
                    } catch (e: Exception) {
                        responseFactory.errorResponse(request.flowExternalEventContext, e)
                    }
                }
            }
    }
}

