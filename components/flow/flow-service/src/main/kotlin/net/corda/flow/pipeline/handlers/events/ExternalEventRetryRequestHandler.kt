package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventRetryRequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.state.impl.CheckpointMetadataKeys
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.statemanager.api.Metadata
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

@Component(service = [FlowEventHandler::class])
class ExternalEventRetryRequestHandler : FlowEventHandler<ExternalEventRetryRequest> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val type = ExternalEventRetryRequest::class.java

    override fun preProcess(context: FlowEventContext<ExternalEventRetryRequest>): FlowEventContext<ExternalEventRetryRequest> {
        val checkpoint = context.checkpoint
        val now = Instant.now()
        val externalEventRetryRequest = context.inputEventPayload
        var metaData = context.metadata
        val messagingConfig = context.configs.getConfig(ConfigKeys.MESSAGING_CONFIG)

        if (!checkpoint.doesExist) {
            log.debug {
                "Received a ${ExternalEventRetryRequest::class.simpleName} for flow [${context.inputEvent.flowId}] that " +
                        "does not exist. The event will be discarded. ${ExternalEventRetryRequest::class.simpleName}: " +
                        externalEventRetryRequest
            }
            throw FlowEventException(
                "ExternalEventResponseHandler received a ${ExternalEventRetryRequest::class.simpleName} for flow" +
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
                "ExternalEventResponseHandler received an ${ExternalEventRetryRequest::class.simpleName} with request id: " +
                        "$retryRequestId while flow [${context.inputEvent.flowId} is not waiting " +
                        "for an ${ExternalEventResponse::class.simpleName}"
            )
        } else if (externalEventStateRequestId == retryRequestId) {
            val expiryTime = getExpiry(metaData)
            if (expiryTime == null) {
                //first retry so time to set an expiry
                metaData = setExpiry(metaData, messagingConfig, now)
            } else if (retryIsExpired(expiryTime, now)) {
                //retry timeout is exceeded so fail the flow
                log.debug {
                    "Received an ${ExternalEventRetryRequest::class.simpleName} with request id: " +
                            "$retryRequestId while flow [${context.inputEvent.flowId} is waiting " +
                            "for an ${ExternalEventResponse::class.simpleName}. " +
                            "${ExternalEventRetryRequest::class.simpleName}: $externalEventRetryRequest. " +
                            "However, transient error retry timeout has expired"
                }
                // Fail the flow gracefully as weve expired the retry timeout.
                throw FlowEventException(
                    "ExternalEventResponseHandler received an ${ExternalEventRetryRequest::class.simpleName} with request id: " +
                            "$retryRequestId, however retry time limit has expired."
                )
            }
        } else {
            log.info("Discarding retry request received with requestId $retryRequestId. This is likely a stale record polled. Checkpoint " +
                    "is currently waiting to receive a response for requestId $externalEventStateRequestId")
        }

        return context.copy(metadata = metaData)
    }

    private fun retryIsExpired(retryTimeout: Long, now: Instant): Boolean {
        return retryTimeout > now.toEpochMilli()
    }

    private fun getExpiry(metaData: Metadata?): Long? {
        if (metaData == null) return null
        val expiry = metaData[CheckpointMetadataKeys.RETRY_EXPIRY] ?: return null
        return expiry as Long
    }

    private fun setExpiry(metaData: Metadata?, messagingConfig: SmartConfig, now: Instant): Metadata {
        val retryTimeout = retryTimeout(messagingConfig) + now.toEpochMilli()
        val newEntry = mapOf(CheckpointMetadataKeys.RETRY_EXPIRY to retryTimeout)
        if (metaData == null) return Metadata(newEntry)
        return Metadata(metaData + newEntry)
    }

    private fun retryTimeout(config: SmartConfig) =
        config.getLong(MessagingConfig.Subscription.MEDIATOR_PROCESSING_TRANSIENT_ERROR_TIMEOUT)
}
