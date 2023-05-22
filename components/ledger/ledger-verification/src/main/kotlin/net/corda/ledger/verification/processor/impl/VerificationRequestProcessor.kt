package net.corda.ledger.verification.processor.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.flow.external.events.responses.exceptions.NotAllowedCpkException
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.flow.utils.toMap
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.ledger.verification.sandbox.VerificationSandboxService
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.MDC_EXTERNAL_EVENT_ID
import net.corda.utilities.selectLoggedProperties
import net.corda.utilities.trace
import net.corda.utilities.withMDC
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.io.NotSerializableException
import java.time.Duration
import java.time.Instant

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
    }

    override val keyClass = String::class.java

    override val valueClass = TransactionVerificationRequest::class.java

    override fun onNext(events: List<Record<String, TransactionVerificationRequest>>): List<Record<*, *>> {
        log.trace { "onNext processing messages ${events.joinToString(",") { it.key }}" }

        return events
            .mapNotNull { it.value }
            .map { request ->
                val startTime = Instant.now()
                val clientRequestId = request.flowExternalEventContext.contextProperties.toMap()[MDC_CLIENT_ID] ?: ""
                val holdingIdentity = request.holdingIdentity.toCorda()

                withMDC(
                    mapOf(
                        MDC_CLIENT_ID to clientRequestId,
                        MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId
                    ) + request.flowExternalEventContext.contextProperties.toMap().selectLoggedProperties()
                ) {
                    try {
                        val sandbox = verificationSandboxService.get(holdingIdentity, request.cpkMetadata)
                        requestHandler.handleRequest(sandbox, request)
                    } catch (e: Exception) {
                        errorResponse(request.flowExternalEventContext, e)
                    }.also {
                        CordaMetrics.Metric.LedgerTransactionVerificationTime
                            .builder()
                            .forVirtualNode(holdingIdentity.shortHash.toString())
                            .withTag(CordaMetrics.Tag.FlowId, request.flowExternalEventContext.flowId)
                            .withTag(CordaMetrics.Tag.LedgerType, LedgerTypes.UTXO.toString())
                            .build()
                            .record(Duration.between(startTime, Instant.now()))
                    }
                }
            }
    }

    private fun errorResponse(externalEventContext : ExternalEventContext, exception: Exception) = when (exception) {
        is NotAllowedCpkException, is NotSerializableException -> {
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


