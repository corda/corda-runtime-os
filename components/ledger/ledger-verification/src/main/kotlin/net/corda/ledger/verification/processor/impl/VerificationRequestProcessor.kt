package net.corda.ledger.verification.processor.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.flow.utils.toMap
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.verification.processor.VerificationErrorType
import net.corda.ledger.verification.processor.VerificationExceptionCategorizer
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.ledger.verification.sandbox.VerificationSandboxService
import net.corda.messaging.api.exception.CordaHTTPServerTransientException
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.MDC_EXTERNAL_EVENT_ID
import net.corda.utilities.translateFlowContextToMDC
import net.corda.utilities.withMDC
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 */
@Suppress("LongParameterList")
class VerificationRequestProcessor(
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    private val verificationSandboxService: VerificationSandboxService,
    private val requestHandler: VerificationRequestHandler,
    private val responseFactory: ExternalEventResponseFactory,
    private val exceptionCategorizer: VerificationExceptionCategorizer
) : SyncRPCProcessor<TransactionVerificationRequest, FlowEvent> {

    override val requestClass = TransactionVerificationRequest::class.java
    override val responseClass = FlowEvent::class.java

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun process(request: TransactionVerificationRequest): FlowEvent {
        val startTime = System.nanoTime()
        val clientRequestId = request.flowExternalEventContext.contextProperties.toMap()[MDC_CLIENT_ID] ?: ""
        val holdingIdentity = request.holdingIdentity.toCorda()
        val result =
            withMDC(
                mapOf(
                    MDC_CLIENT_ID to clientRequestId,
                    MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId
                ) + translateFlowContextToMDC(request.flowExternalEventContext.contextProperties.toMap())
            ) {
                try {
                    val sandbox = verificationSandboxService.get(holdingIdentity, request.cpkMetadata)
                    currentSandboxGroupContext.set(sandbox)
                    requestHandler.handleRequest(sandbox, request)
                } catch (e: Exception) {
                    return@withMDC when (exceptionCategorizer.categorize(e)) {
                        VerificationErrorType.FATAL -> fatalErrorResponse(request.flowExternalEventContext, e)
                        VerificationErrorType.PLATFORM -> platformErrorResponse(request.flowExternalEventContext, e)
                        VerificationErrorType.TRANSIENT -> throw CordaHTTPServerTransientException(
                            request.flowExternalEventContext.requestId,
                            e
                        )
                        VerificationErrorType.RETRYABLE -> throw e
                    }
                } finally {
                    currentSandboxGroupContext.remove()
                }.also {
                    CordaMetrics.Metric.Ledger.TransactionVerificationTime
                        .builder()
                        .forVirtualNode(holdingIdentity.shortHash.toString())
                        .build()
                        .record(Duration.ofNanos(System.nanoTime() - startTime))
                }
            }
        return result.value as FlowEvent
    }

    private fun fatalErrorResponse(
        externalEventContext: ExternalEventContext,
        exception: Exception
    ): Record<String, FlowEvent> {
        log.warn(errorMessage(externalEventContext, ExternalEventResponseErrorType.FATAL), exception)
        return responseFactory.fatalError(externalEventContext, exception)
    }
    private fun platformErrorResponse(externalEventContext: ExternalEventContext, exception: Exception): Record<String, FlowEvent> {
        log.warn(errorMessage(externalEventContext, ExternalEventResponseErrorType.PLATFORM), exception)
        return responseFactory.platformError(externalEventContext, exception)
    }

    private fun errorMessage(
        externalEventContext: ExternalEventContext,
        errorType: ExternalEventResponseErrorType
    ) = "Exception occurred (type=$errorType) for verification-worker request ${externalEventContext.requestId}"
}
