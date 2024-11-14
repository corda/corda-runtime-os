package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventRetryRequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Handles pre-processing of events that are intended to trigger a resend of an external event.
 * This can be triggered by the mediator in the event of transient errors.
 */
@Component(service = [FlowEventHandler::class])
class ExternalEventRetryRequestHandler : FlowEventHandler<ExternalEventRetryRequest> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val TOKEN_RETRY = "TokenRetry"
    }

    override val type = ExternalEventRetryRequest::class.java

    override fun preProcess(context: FlowEventContext<ExternalEventRetryRequest>): FlowEventContext<ExternalEventRetryRequest> {
        val checkpoint = context.checkpoint
        val externalEventRetryRequest = context.inputEventPayload

        if (!checkpoint.doesExist) {
            log.debug {
                "Received a ${ExternalEventRetryRequest::class.simpleName} for flow [${context.inputEvent.flowId}] that " +
                        "does not exist. The event will be discarded. ${ExternalEventRetryRequest::class.simpleName}: " +
                        externalEventRetryRequest
            }
            throw FlowEventException(
                "ExternalEventRetryRequestHandler received a ${ExternalEventRetryRequest::class.simpleName} for flow" +
                        " [${context.inputEvent.flowId}] that does not exist"
            )
        }

        val externalEventState = checkpoint.externalEventState
        val retryRequestId: String = externalEventRetryRequest.requestId
        val externalEventStateRequestId = externalEventState?.requestId
        if (externalEventState == null) {
            log.debug {
                "Received an ${ExternalEventRetryRequest::class.simpleName} with request id: " +
                        "$retryRequestId while flow [${context.inputEvent.flowId} is not waiting " +
                        "for an ${ExternalEventResponse::class.simpleName}. " +
                        "${ExternalEventRetryRequest::class.simpleName}: $externalEventRetryRequest"
            }
            throw FlowEventException(
                "ExternalEventRetryRequestHandler received an ${ExternalEventRetryRequest::class.simpleName} with request id: " +
                        "$retryRequestId while flow [${context.inputEvent.flowId} is not waiting " +
                        "for an ${ExternalEventResponse::class.simpleName}"
            )
        }
        //Discard events not related. Some token requests do not contain the external event id so this validation will allow all token
        // requests to be resent. e.g TokenForceClaimRelease
        else if (externalEventStateRequestId != retryRequestId && retryRequestId != TOKEN_RETRY) {
            throw FlowEventException(
                "Discarding retry request received with requestId $retryRequestId. This is likely a stale record polled. Checkpoint " +
                        "is currently waiting to receive a response for requestId $externalEventStateRequestId"
            )
        }

        return context
    }
}
