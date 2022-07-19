package net.corda.flow.pipeline.handlers.events

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.external.ExternalEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateStatus
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig.EXTERNAL_EVENT_MAX_RETRIES
import net.corda.schema.configuration.FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.apache.avro.specific.SpecificRecord
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant
import java.util.UUID

// Do not serialize the [eventToSend] as it is only needed when leaving the fiber and executed in the request handler
data class ExternalEventRequest(
    val requestId: String = UUID.randomUUID().toString(),
    @Transient val eventToSend: EventToSend
) : FlowIORequest<ExternalEventRequest.Response> {

    fun interface EventToSend {
        fun createRecord(requestId: String): EventRecord
    }

    // Can't decide whether to just use [Record] directly at this point
    // We need to be able to specify a key if the external event processors want to shard by their own key
    data class EventRecord(val topic: String, val key: Any?, val payload: SpecificRecord) {
        constructor(topic: String, payload: SpecificRecord) : this(topic, null, payload)
    }

    data class Response(val lastEvent: Any?, val data: ByteArray?) {

        inline fun <reified T> lastEventAs(): T = lastEvent as T
    }
}

@Component(service = [FlowEventHandler::class])
class FlowExternalEventHandler @Activate constructor(
    @Reference(service = ExternalEventManager::class)
    private val externalEventManager: ExternalEventManager
) : FlowEventHandler<ExternalEventResponse> {

    override val type = ExternalEventResponse::class.java

    override fun preProcess(context: FlowEventContext<ExternalEventResponse>): FlowEventContext<ExternalEventResponse> {
        val checkpoint = context.checkpoint
        val externalEventResponse = context.inputEventPayload
        val externalEventState = checkpoint.externalEventState
        if (externalEventState == null) {
            // do something, probably discard the event
        } else {
            checkpoint.externalEventState = externalEventManager.processEventReceived(externalEventState, externalEventResponse)
        }
        return context
    }
}

@Component(service = [FlowWaitingForHandler::class])
class ExternalEventResponseWaitingForHandler @Activate constructor(
    @Reference(service = ExternalEventManager::class)
    private val externalEventManager: ExternalEventManager
) : FlowWaitingForHandler<net.corda.data.flow.state.waiting.external.ExternalEventResponse> {

    private companion object {
        val log = contextLogger()
    }

    override val type = net.corda.data.flow.state.waiting.external.ExternalEventResponse::class.java

    override fun runOrContinue(
        context: FlowEventContext<*>,
        waitingFor: net.corda.data.flow.state.waiting.external.ExternalEventResponse
    ): FlowContinuation {
        val externalEventState =
            context.checkpoint.externalEventState ?: throw FlowFatalException("Waiting for external event but state not set", context)

        val continuation = when (externalEventState.status.type) {
            ExternalEventStateType.OK -> {
                when (val externalEventResponse = externalEventManager.getReceivedResponse(externalEventState)) {
                    null -> FlowContinuation.Continue
                    else -> {
                        FlowContinuation.Run(externalEventResponse)
                    }
                }
            }
            ExternalEventStateType.RETRY -> {
                retryOrError(context.config, externalEventState.status.exception, externalEventState)
            }
            ExternalEventStateType.PLATFORM_ERROR -> {
                FlowContinuation.Error(CordaRuntimeException(externalEventState.status.exception.errorMessage))
            }
            ExternalEventStateType.FATAL_ERROR -> {
                throw FlowFatalException(externalEventState.status.exception.errorMessage, context)
            }
            null -> throw FlowFatalException(
                "Unexpected null ${ExternalEventStateType::class.java.name} for flow ${context.checkpoint.flowId}",
                context
            )
        }

        if (continuation != FlowContinuation.Continue) {
            context.checkpoint.externalEventState = null
        }

        return continuation
    }

    private fun retryOrError(config: SmartConfig, exception: ExceptionEnvelope, externalEventState: ExternalEventState): FlowContinuation {
        val retries = externalEventState.retries
        // external event config needed
        return if (retries >= config.getLong(EXTERNAL_EVENT_MAX_RETRIES)) {
            log.error("Retriable exception received from the external event response. Exceeded max retries. Exception: $exception")
            FlowContinuation.Error(CordaRuntimeException(exception.errorMessage))
        } else {
            log.warn("Retriable exception received from the external event response. Retrying exception after delay. Current retry count $retries. Exception: $exception")
            externalEventState.retries = retries.inc()
            FlowContinuation.Continue
        }
    }
}

@Component(service = [FlowRequestHandler::class])
class ExternalEventRequestHandler @Activate constructor(
    @Reference(service = ExternalEventManager::class)
    private val externalEventManager: ExternalEventManager
) : FlowRequestHandler<ExternalEventRequest> {

    override val type = ExternalEventRequest::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: ExternalEventRequest): WaitingFor {
        return WaitingFor(net.corda.data.flow.state.waiting.external.ExternalEventResponse(request.requestId))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: ExternalEventRequest): FlowEventContext<Any> {
        val eventRecord = request.eventToSend.createRecord(request.requestId)
        context.checkpoint.externalEventState = externalEventManager.processEventToSend(
            context.checkpoint.flowId,
            request.requestId,
            eventRecord,
            Instant.now()
        )
        return context
    }
}

interface ExternalEventManager {

    fun processEventToSend(
        flowId: String,
        requestId: String,
        eventRecord: ExternalEventRequest.EventRecord,
        instant: Instant
    ): ExternalEventState

    fun processEventReceived(externalEventState: ExternalEventState, externalEventResponse: ExternalEventResponse): ExternalEventState

    fun getReceivedResponse(externalEventState: ExternalEventState): ExternalEventRequest.Response?

    fun getEventToSend(
        flowId: String,
        externalEventState: ExternalEventState,
        instant: Instant,
        config: SmartConfig
    ): Pair<ExternalEventState, Record<*, ExternalEvent>?>
}

@Component(service = [ExternalEventManager::class])
class ExternalEventManagerImpl : ExternalEventManager {

    private companion object {
        val logger = contextLogger()
        const val INSTANT_COMPARE_BUFFER = 100L
    }

    override fun processEventToSend(
        flowId: String,
        requestId: String,
        eventRecord: ExternalEventRequest.EventRecord,
        instant: Instant
    ): ExternalEventState {
        logger.debug { "Processing external event response of type ${eventRecord.payload.javaClass.name} with id $requestId" }
        val event = ExternalEvent.newBuilder()
            .setRequestId(requestId)
            .setFlowId(flowId)
            .setTopic(eventRecord.topic)
            .setKey(eventRecord.key ?: flowId)
            .setPayload(eventRecord.payload)
            .setTimestamp(instant)
            .build()
        return ExternalEventState.newBuilder()
            .setRequestId(requestId)
            .setStatus(ExternalEventStateStatus(ExternalEventStateType.OK, null))
            .setEventToSend(event)
            .setSendTimestamp(instant)
            .setResponses(mutableListOf())
            .build()
    }

    // I need the exception so that I can return an error with the correct error message to the flow
    // either store the exception on the status (change the status to a class with an enum + nullable exception) or use the response itself
    override fun processEventReceived(
        externalEventState: ExternalEventState,
        externalEventResponse: ExternalEventResponse
    ): ExternalEventState {
        val requestId = externalEventResponse.requestId
        logger.debug { "Processing received external event response of type ${externalEventResponse.payload.javaClass.name} with id $requestId" }
        if (requestId == externalEventState.requestId) {
            logger.debug { "External event response with id $requestId matched last sent request" }
            val exceptionEnvelope = externalEventResponse.exceptionEnvelope
            if (exceptionEnvelope != null) {
                externalEventState.status = when (exceptionEnvelope.errorType) {
                    ExternalEventResponseErrorType.RETRY -> {
                        ExternalEventStateStatus(ExternalEventStateType.RETRY, exceptionEnvelope.exception)
                    }
                    ExternalEventResponseErrorType.PLATFORM_ERROR -> {
                        ExternalEventStateStatus(ExternalEventStateType.PLATFORM_ERROR, exceptionEnvelope.exception)
                    }
                    ExternalEventResponseErrorType.FATAL_ERROR -> {
                        ExternalEventStateStatus(ExternalEventStateType.FATAL_ERROR, exceptionEnvelope.exception)
                    }
                    else -> throw IllegalArgumentException("Unexpected null ${Error::class.java.name} for external event with request id $requestId")
                }
                // Simple implementation to handle error but probably needs revisiting when chunking is looked at properly.
                externalEventState.responses = listOf(externalEventResponse)
            } else {
                require(externalEventState.responses.isEmpty() || externalEventState.responses.first().numberOfChunks == externalEventResponse.numberOfChunks) {
                    "The total number of chunks unexpectedly changed from ${externalEventState.responses.first().numberOfChunks} to ${externalEventResponse.numberOfChunks}"
                }
                externalEventState.responses.add(externalEventResponse)
            }
        }
        return externalEventState
    }

    override fun getReceivedResponse(externalEventState: ExternalEventState): ExternalEventRequest.Response? {
        return if (!isWaitingForResponse(externalEventState)) {
            val sortedResponses = externalEventState.responses.sortedBy { response -> response.chunkNumber }
            val data = if (sortedResponses.first().numberOfChunks != null) {
                var totalBytes = byteArrayOf()
                for (response in sortedResponses) {
                    totalBytes += response.data.array()
                }
                totalBytes
            } else {
                sortedResponses.last().data?.array()
            }
            ExternalEventRequest.Response(sortedResponses.last().payload, data)
        } else {
            null
        }
    }

    override fun getEventToSend(
        flowId: String,
        externalEventState: ExternalEventState,
        instant: Instant,
        config: SmartConfig
    ): Pair<ExternalEventState, Record<*, ExternalEvent>?> {
        return if (isWaitingForResponse(externalEventState) && isSendWindowValid(externalEventState, instant)) {
            val eventToSend = externalEventState.eventToSend
            // Have a "sending" and "resending" log line
            logger.debug { "Resending external event request which was last sent at ${eventToSend.timestamp}" }
            // need to handle crypto or persistence config - just have external event config that covers all
            eventToSend.timestamp = instant
            externalEventState.sendTimestamp = instant.plusMillis(config.getLong(EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW))
            externalEventState to Record(eventToSend.topic, eventToSend.key, eventToSend)
        } else {
            externalEventState to null
        }
    }

    private fun isWaitingForResponse(externalEventState: ExternalEventState): Boolean {
        val numberOfChunks = externalEventState.responses.firstOrNull()?.numberOfChunks
        val numberOfChunksReceived = externalEventState.responses.maxOfOrNull { response -> response.chunkNumber ?: 0 }
        return externalEventState.responses.isEmpty() || numberOfChunks != null && numberOfChunks != numberOfChunksReceived
    }

    private fun isSendWindowValid(externalEventState: ExternalEventState, instant: Instant): Boolean {
        return externalEventState.sendTimestamp.toEpochMilli() < (instant.toEpochMilli() + INSTANT_COMPARE_BUFFER)
    }
}