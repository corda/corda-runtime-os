package net.corda.flow.external.events.impl

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.external.ExternalEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateStatus
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.external.events.handler.ExternalEventRecord
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ExternalEventManager::class])
class ExternalEventManagerImpl(
    private val serializer: CordaAvroSerializer<Any>,
    private val deserializer: CordaAvroDeserializer<Any>
) : ExternalEventManager {

    @Activate
    constructor(
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    ) : this(
        cordaAvroSerializationFactory.createAvroSerializer<Any> {},
        cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)
    )

    private companion object {
        val logger = contextLogger()
        //Comparing two instants which are the same can yield inconsistent comparison results.
        //A small buffer is added to make sure we pick up new messages to be sent
        const val INSTANT_COMPARE_BUFFER_MILLIS = 10L
    }

    override fun processEventToSend(
        flowId: String,
        requestId: String,
        handlerClassName: String,
        eventRecord: ExternalEventRecord,
        instant: Instant
    ): ExternalEventState {
        logger.debug {
            "Processing external event response of type ${eventRecord.payload.javaClass.name} with id $requestId"
        }

        val event = ExternalEvent.newBuilder()
            .setTopic(eventRecord.topic)
            .setKey(ByteBuffer.wrap(serializer.serialize(eventRecord.key ?: flowId)))
            .setPayload(ByteBuffer.wrap(serializer.serialize(eventRecord.payload)))
            .setTimestamp(instant)
            .build()
        return ExternalEventState.newBuilder()
            .setRequestId(requestId)
            .setStatus(ExternalEventStateStatus(ExternalEventStateType.OK, null))
            .setEventToSend(event)
            .setHandlerClassName(handlerClassName)
            .setSendTimestamp(instant)
            .setResponse(null)
            .build()
    }

    override fun processEventReceived(
        externalEventState: ExternalEventState,
        externalEventResponse: ExternalEventResponse
    ): ExternalEventState {
        val requestId = externalEventResponse.requestId
        logger.debug {
            "Processing received external event response of type ${externalEventResponse.payload.javaClass.name} " +
                    "with id $requestId"
        }
        if (requestId == externalEventState.requestId) {
            logger.debug { "External event response with id $requestId matched last sent request" }
            externalEventState.response = externalEventResponse
            externalEventResponse.error?.let { error ->
                externalEventState.status = when (error.errorType) {
                    ExternalEventResponseErrorType.RETRY -> {
                        ExternalEventStateStatus(ExternalEventStateType.RETRY, error.exception)
                    }
                    ExternalEventResponseErrorType.PLATFORM_ERROR -> {
                        ExternalEventStateStatus(ExternalEventStateType.PLATFORM_ERROR, error.exception)
                    }
                    ExternalEventResponseErrorType.FATAL_ERROR -> {
                        ExternalEventStateStatus(ExternalEventStateType.FATAL_ERROR, error.exception)
                    }
                    else -> throw FlowFatalException(
                        "Unexpected null ${Error::class.java.name} for external event with request id $requestId"
                    )
                }
            }
        }
        return externalEventState
    }

    override fun getReceivedResponse(externalEventState: ExternalEventState): Any? {
        return if (!isWaitingForResponse(externalEventState)) {
            deserializer.deserialize(externalEventState.response.payload.array())
        } else {
            null
        }
    }

    override fun getEventToSend(
        flowId: String,
        externalEventState: ExternalEventState,
        instant: Instant,
        config: SmartConfig
    ): Pair<ExternalEventState, Record<*, *>?> {
        return if (isWaitingForResponse(externalEventState) && isSendWindowValid(externalEventState, instant)) {
            val eventToSend = externalEventState.eventToSend
            logger.debug { "Resending external event request which was last sent at ${eventToSend.timestamp}" }
            eventToSend.timestamp = instant
            externalEventState.sendTimestamp = instant.plusMillis(config.getLong(FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW))
            externalEventState to Record(eventToSend.topic, eventToSend.key.array(), eventToSend.payload.array())
        } else {
            externalEventState to null
        }
    }

    private fun isWaitingForResponse(externalEventState: ExternalEventState): Boolean {
        return externalEventState.response == null
    }

    private fun isSendWindowValid(externalEventState: ExternalEventState, instant: Instant): Boolean {
        return externalEventState.sendTimestamp.toEpochMilli() < (instant.toEpochMilli() + INSTANT_COMPARE_BUFFER_MILLIS)
    }
}