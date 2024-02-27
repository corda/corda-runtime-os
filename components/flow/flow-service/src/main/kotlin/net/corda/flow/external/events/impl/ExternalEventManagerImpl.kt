package net.corda.flow.external.events.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.external.ExternalEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateStatus
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.messaging.api.records.Record
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

@Component(service = [ExternalEventManager::class])
class ExternalEventManagerImpl(
    private val serializer: CordaAvroSerializer<Any>,
    private val stringDeserializer: CordaAvroDeserializer<String>,
    private val byteArrayDeserializer: CordaAvroDeserializer<ByteArray>,
    private val anyDeserializer: CordaAvroDeserializer<Any>
) : ExternalEventManager {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        log.trace { "Processing response for external event with id '$requestId'" }

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
        retryWindow: Duration
    ): Pair<ExternalEventState, Record<*, *>?> {
        val sendTimestamp = externalEventState.sendTimestamp
        val record = when (externalEventState.status.type) {
            ExternalEventStateType.OK -> {
                if (sendTimestamp == null) {
                    externalEventState.sendTimestamp = instant
                    externalEventState.retries = 0
                    generateRecord(externalEventState, instant)
                } else {
                    null
                }
            }
            ExternalEventStateType.RETRY -> {
                checkRetry(externalEventState, instant, retryWindow)
                // Hacky wacky. Will be removed very soon
                Thread.sleep(externalEventState.retries * 100L)
                generateRecord(externalEventState, instant)
            }
            else -> {
                null
            }
        }
        return externalEventState to record
    }

    private fun checkRetry(externalEventState: ExternalEventState, instant: Instant, retryWindow: Duration) {
        when {
            (externalEventState.sendTimestamp + retryWindow) >= instant -> {
                // Do nothing. This check ensures that subsequent branches are checking the case where the external
                // event is outside the retry window.
            }
            externalEventState.retries == 0 -> {
                // Use the retries field to indicate how many times the event has been retried outside the window.
                // Retrying once outside the window is required in case the flow engine receives the event to trigger
                // the retry late. This guarantees an external event will be tried at least twice. After that though,
                // retrying further is unlikely to clear the problem.
                externalEventState.retries++
            }
            else -> {
                throw FlowFatalException("External event with request ID ${externalEventState.requestId} exceeded " +
                        "the retry window.")
            }
        }
    }

    private fun generateRecord(externalEventState: ExternalEventState, instant: Instant) : Record<*, *> {
        val eventToSend = externalEventState.eventToSend
        eventToSend.timestamp = instant
        val topic = eventToSend.topic
        log.trace { "Dispatching external event with id '${externalEventState.requestId}' to '$topic'" }
        return Record(topic, eventToSend.key.array(), eventToSend.payload.array())
    }
}
