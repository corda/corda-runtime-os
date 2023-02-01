package net.corda.flow.external.events.impl

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.external.ExternalEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateStatus
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component(service = [ExternalEventManager::class])
class ExternalEventManagerImpl(
    private val serializer: CordaAvroSerializer<Any>,
    private val stringDeserializer: CordaAvroDeserializer<String>,
    private val byteArrayDeserializer: CordaAvroDeserializer<ByteArray>,
    private val anyDeserializer: CordaAvroDeserializer<Any>
) : ExternalEventManager {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Activate
    constructor(
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    ) : this(
        cordaAvroSerializationFactory.createAvroSerializer<Any> {},
        cordaAvroSerializationFactory.createAvroDeserializer({}, String::class.java),
        cordaAvroSerializationFactory.createAvroDeserializer({}, ByteArray::class.java),
        cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)
    )

    override fun processEventToSend(
        flowId: String,
        requestId: String,
        factoryClassName: String,
        eventRecord: ExternalEventRecord,
        instant: Instant
    ): ExternalEventState {
        log.debug {
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
            .setFactoryClassName(factoryClassName)
            .setSendTimestamp(null)
            .setResponse(null)
            .build()
    }

    override fun processResponse(
        externalEventState: ExternalEventState,
        externalEventResponse: ExternalEventResponse
    ): ExternalEventState {
        val requestId = externalEventResponse.requestId
        log.debug { "Processing received external event response with id $requestId" }
        if (requestId == externalEventState.requestId) {
            log.debug { "External event response with id $requestId matched last sent request" }
            externalEventState.response = externalEventResponse

            if (externalEventResponse.error == null) {
                if (externalEventState.status.type != ExternalEventStateType.OK) {
                    externalEventState.status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
                }
            } else {
                val error = externalEventResponse.error
                val exception = error.exception
                externalEventState.status = when (error.errorType) {
                    ExternalEventResponseErrorType.TRANSIENT -> {
                        log.debug {
                            "Received a transient error in external event response: $exception. Updating external " +
                                    "event status to RETRY."
                        }
                        ExternalEventStateStatus(ExternalEventStateType.RETRY, exception)
                    }
                    ExternalEventResponseErrorType.PLATFORM -> {
                        log.debug {
                            "Received a platform error in external event response: $exception. Updating external " +
                                    "event status to PLATFORM_ERROR."
                        }
                        ExternalEventStateStatus(ExternalEventStateType.PLATFORM_ERROR, exception)
                    }
                    ExternalEventResponseErrorType.FATAL -> {
                        log.debug {
                            "Received a fatal error in external event response: $exception. Updating external event " +
                                    "status to FATAL_ERROR."
                        }
                        ExternalEventStateStatus(ExternalEventStateType.FATAL_ERROR, exception)
                    }
                    else -> throw FlowFatalException(
                        "Unexpected null ${Error::class.java.name} for external event with request id $requestId"
                    )
                }
            }
        } else {
            log.warn(
                "Received an external event response with id $requestId when waiting for a response with id " +
                        "${externalEventState.requestId}. This response will be discarded. Content of the response: " +
                        externalEventResponse
            )
        }
        return externalEventState
    }

    override fun hasReceivedResponse(externalEventState: ExternalEventState): Boolean {
        return externalEventState.response != null
    }

    override fun getReceivedResponse(externalEventState: ExternalEventState, responseType: Class<*>): Any {
        val bytes = checkNotNull(externalEventState.response).payload.array()
        val deserialized = when (responseType) {
            String::class.java -> stringDeserializer.deserialize(bytes)
            ByteArray::class.java -> byteArrayDeserializer.deserialize(bytes)
            else -> anyDeserializer.deserialize(bytes)
        }
        return checkNotNull(deserialized)
    }

    override fun getEventToSend(
        externalEventState: ExternalEventState,
        instant: Instant,
        config: SmartConfig
    ): Pair<ExternalEventState, Record<*, *>?> {
        return when {
            hasNotSentOriginalEvent(externalEventState) -> {
                log.debug {
                    "Sending external event request ${externalEventState.requestId} " +
                            externalEventState.eventToSend
                }
                getAndUpdateEventToSend(externalEventState, instant, config)
            }
            canRetryEvent(externalEventState, instant) -> {
                log.debug {
                    "Resending external event request ${externalEventState.requestId} which was last sent at " +
                            externalEventState.eventToSend.timestamp
                }
                getAndUpdateEventToSend(externalEventState, instant, config)
            }
            else -> externalEventState to null
        }
    }

    private fun hasNotSentOriginalEvent(externalEventState: ExternalEventState): Boolean {
        return externalEventState.sendTimestamp == null
    }

    private fun canRetryEvent(externalEventState: ExternalEventState, instant: Instant): Boolean {
        return if (externalEventState.status.type == ExternalEventStateType.RETRY) {
            val sendTimestamp = externalEventState.sendTimestamp.truncatedTo(ChronoUnit.MILLIS).toEpochMilli()
            val currentTimestamp = instant.truncatedTo(ChronoUnit.MILLIS).toEpochMilli()
            sendTimestamp < currentTimestamp
        } else {
            false
        }
    }

    private fun getAndUpdateEventToSend(
        externalEventState: ExternalEventState,
        instant: Instant,
        config: SmartConfig
    ): Pair<ExternalEventState, Record<*, *>?> {
        val eventToSend = externalEventState.eventToSend
        eventToSend.timestamp = instant
        externalEventState.sendTimestamp = instant.plusMillis(config.getLong(FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW))
        return externalEventState to Record(eventToSend.topic, eventToSend.key.array(), eventToSend.payload.array())
    }
}
