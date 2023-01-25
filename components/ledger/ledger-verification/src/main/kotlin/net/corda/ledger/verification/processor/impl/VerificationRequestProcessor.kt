package net.corda.ledger.verification.processor.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponseErrorType.PLATFORM
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequestRedelivery
import net.corda.ledger.utxo.contract.verification.VerifyContractsResponse
import net.corda.ledger.verification.exceptions.NotReadyException
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.ledger.verification.sanbox.VerificationSandboxService
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.withMDC
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.toCorda
import java.time.Duration
import java.time.Instant

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 */
class VerificationRequestProcessor(
    private val verificationSandboxService: VerificationSandboxService,
    private val requestHandler: VerificationRequestHandler,
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : StateAndEventProcessor<String, VerifyContractsRequestRedelivery, VerifyContractsRequest> {

    private companion object {
        val log = contextLogger()
        const val MDC_EXTERNAL_EVENT_ID = "external_event_id"
        const val MAX_REDELIVERIES = 3
        val REDELIVERY_DELAY: Duration = Duration.ofSeconds(10)
    }

    override val keyClass = String::class.java
    override val stateValueClass = VerifyContractsRequestRedelivery::class.java
    override val eventValueClass = VerifyContractsRequest::class.java

    override fun onNext(
        state: VerifyContractsRequestRedelivery?,
        event: Record<String, VerifyContractsRequest>
    ): StateAndEventProcessor.Response<VerifyContractsRequestRedelivery> {
        log.trace { "onNext processing message $event" }

        return event.value?.let { request ->
            withMDC(mapOf(MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId)) {
                try {
                    val holdingIdentity = request.holdingIdentity.toCorda()
                    val cpkIds = request.cpkMetadata.mapTo(mutableSetOf()) { SecureHash.parse(it.fileChecksum) }
                    val sandbox = verificationSandboxService.get(holdingIdentity, cpkIds)
                    successfulResponse(request, requestHandler.handleRequest(sandbox, request))
                } catch (e: NotReadyException) {
                    if (state != null && state.redeliveryNumber >= MAX_REDELIVERIES) {
                        errorResponse(request, e)
                    } else {
                        redeliverRequest(state, request)
                    }
                } catch (e: Exception) {
                    errorResponse(request, e)
                }
            }
        } ?: StateAndEventProcessor.Response(null, emptyList())
    }

    private fun redeliverRequest(
        state: VerifyContractsRequestRedelivery?,
        request: VerifyContractsRequest
    ): StateAndEventProcessor.Response<VerifyContractsRequestRedelivery> {
        val now = Instant.now()
        val redeliveryNumber = (state?.redeliveryNumber ?: 0) + 1
        return StateAndEventProcessor.Response(
            VerifyContractsRequestRedelivery(
                now,
                now.plus(REDELIVERY_DELAY),
                redeliveryNumber,
                request
            ),
            emptyList()
        )
    }

    private fun successfulResponse(request: VerifyContractsRequest, response: VerifyContractsResponse) =
        StateAndEventProcessor.Response<VerifyContractsRequestRedelivery>(
            null,
            listOf(externalEventResponseFactory.success(request.flowExternalEventContext, response))
        )

    private fun errorResponse(
        request: VerifyContractsRequest, e: Exception
    ) : StateAndEventProcessor.Response<VerifyContractsRequestRedelivery> {
        log.warn(errorLogMessage(request.flowExternalEventContext), e)
        return StateAndEventProcessor.Response(
            null,
            listOf(externalEventResponseFactory.platformError(request.flowExternalEventContext, e))
        )
    }

    private fun errorLogMessage(flowExternalEventContext: ExternalEventContext): String {
        return "Exception occurred (type=$PLATFORM) for flow-worker request ${flowExternalEventContext.requestId}"
    }
}

