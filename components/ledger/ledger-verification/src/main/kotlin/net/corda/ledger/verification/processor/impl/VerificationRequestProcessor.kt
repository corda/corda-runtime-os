package net.corda.ledger.verification.processor.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.ledger.verification.sandbox.VerificationSandboxService
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.withMDC
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.io.NotSerializableException

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 */
@Suppress("LongParameterList")
class VerificationRequestProcessor(
    private val verificationSandboxService: VerificationSandboxService,
    private val requestHandler: VerificationRequestHandler,
    private val responseFactory: ExternalEventResponseFactory
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
                        errorResponse(request.flowExternalEventContext, e)
                    }
                }
            }
    }

    private fun errorResponse(externalEventContext : ExternalEventContext, exception: Exception) = when (exception) {
        is NotSerializableException -> {
            log.error(errorMessage(externalEventContext, ExternalEventResponseErrorType.PLATFORM), exception)
            responseFactory.platformError(externalEventContext, exception)
        } else -> {
            log.warn(errorMessage(externalEventContext, ExternalEventResponseErrorType.TRANSIENT), exception)
            responseFactory.transientError(externalEventContext, exception)
        }
    }

    private fun errorMessage(
        externalEventContext: ExternalEventContext,
        errorType: ExternalEventResponseErrorType
    ) = "Exception occurred (type=$errorType) for flow-worker request ${externalEventContext.requestId}"
}


