package net.corda.flow.external.events.impl

import java.nio.ByteBuffer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.stream.Stream
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.event.external.ExternalEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseError
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateStatus
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.FLOW_ID_1
import net.corda.flow.REQUEST_ID_1
import net.corda.flow.application.crypto.external.events.CreateSignatureExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.pipeline.exceptions.FlowFatalException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Duration

class ExternalEventManagerImplTest {

    private companion object {
        const val TOPIC = "topic"
        const val KEY = "key"
        val BYTES = byteArrayOf(1, 2, 3)

        @JvmStatic
        fun responses(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "Successful",
                    ExternalEventResponse().apply {
                        requestId = REQUEST_ID_1
                    }
                ),
                Arguments.of(
                    "Transient",
                    ExternalEventResponse().apply {
                        requestId = REQUEST_ID_1
                        error = ExternalEventResponseError(
                            ExternalEventResponseErrorType.TRANSIENT,
                            ExceptionEnvelope()
                        )
                    }
                ),
                Arguments.of(
                    "Platform error",
                    ExternalEventResponse().apply {
                        requestId = REQUEST_ID_1
                        error = ExternalEventResponseError(
                            ExternalEventResponseErrorType.PLATFORM,
                            ExceptionEnvelope()
                        )
                    }
                ),
                Arguments.of(
                    "Fatal error",
                    ExternalEventResponse().apply {
                        requestId = REQUEST_ID_1
                        error = ExternalEventResponseError(
                            ExternalEventResponseErrorType.FATAL,
                            ExceptionEnvelope()
                        )
                    }
                )
            )
        }

        @JvmStatic
        fun errorResponses(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(ExternalEventStateType.RETRY, ExternalEventResponseErrorType.TRANSIENT),
                Arguments.of(ExternalEventStateType.PLATFORM_ERROR, ExternalEventResponseErrorType.PLATFORM),
                Arguments.of(ExternalEventStateType.FATAL_ERROR, ExternalEventResponseErrorType.FATAL)
            )
        }
    }

    private val serializer = mock<CordaAvroSerializer<Any>>()
    private val stringDeserializer = mock<CordaAvroDeserializer<String>>()
    private val byteArrayDeserializer = mock<CordaAvroDeserializer<ByteArray>>()
    private val anyDeserializer = mock<CordaAvroDeserializer<Any>>()

    private val externalEventManager = ExternalEventManagerImpl(
        serializer,
        stringDeserializer,
        byteArrayDeserializer,
        anyDeserializer
    )

    @Test
    fun `processEventToSend creates external event and state`() {
        val payload = FlowOpsRequest()

        whenever(serializer.serialize(KEY)).thenReturn(KEY.toByteArray())
        whenever(serializer.serialize(payload)).thenReturn(BYTES)

        val externalEventState = externalEventManager.processEventToSend(
            FLOW_ID_1,
            REQUEST_ID_1,
            CreateSignatureExternalEventFactory::class.java.name,
            ExternalEventRecord(TOPIC, KEY, payload),
            Instant.now()
        )

        assertEquals(externalEventState.eventToSend.topic, TOPIC)
        assertEquals(externalEventState.eventToSend.key, ByteBuffer.wrap(KEY.toByteArray()))
        assertEquals(externalEventState.eventToSend.payload, ByteBuffer.wrap(BYTES))
        assertEquals(externalEventState.requestId, REQUEST_ID_1)
        assertEquals(externalEventState.status.type, ExternalEventStateType.OK)
        assertEquals(externalEventState.factoryClassName, CreateSignatureExternalEventFactory::class.java.name)
        assertNull(externalEventState.sendTimestamp)
        assertNull(externalEventState.response)
    }

    @Test
    fun `processEventToSend uses the flow id if the external event record has not set a key`() {
        val payload = FlowOpsRequest()
        val serializedPayload = BYTES

        whenever(serializer.serialize(FLOW_ID_1)).thenReturn(FLOW_ID_1.toByteArray())
        whenever(serializer.serialize(payload)).thenReturn(serializedPayload)

        val externalEventState = externalEventManager.processEventToSend(
            FLOW_ID_1,
            REQUEST_ID_1,
            CreateSignatureExternalEventFactory::class.java.name,
            ExternalEventRecord(TOPIC, key = null, payload),
            Instant.now()
        )

        assertEquals(externalEventState.eventToSend.topic, TOPIC)
        assertEquals(externalEventState.eventToSend.key, ByteBuffer.wrap(FLOW_ID_1.toByteArray()))
        assertEquals(externalEventState.eventToSend.payload, ByteBuffer.wrap(serializedPayload))
        assertEquals(externalEventState.requestId, REQUEST_ID_1)
        assertEquals(externalEventState.status.type, ExternalEventStateType.OK)
        assertEquals(externalEventState.factoryClassName, CreateSignatureExternalEventFactory::class.java.name)
        assertNull(externalEventState.sendTimestamp)
        assertNull(externalEventState.response)
    }

    @ParameterizedTest(name = "processEventReceived sets the state's response when a {0} response is received")
    @MethodSource("responses")
    fun `processEventReceived sets the state's response`(
        @Suppress("UNUSED_PARAMETER") type: String,
        externalEventResponse: ExternalEventResponse
    ) {
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
        }
        val updatedExternalEventState = externalEventManager.processResponse(
            externalEventState,
            externalEventResponse
        )

        assertEquals(externalEventResponse, updatedExternalEventState.response)
    }

    @Test
    fun `processEventReceived sets the state's status to OK if the status was retry before and a successful response was received`() {
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            status = ExternalEventStateStatus(ExternalEventStateType.RETRY, ExceptionEnvelope())
        }
        val updatedExternalEventState = externalEventManager.processResponse(
            externalEventState,
            ExternalEventResponse().apply {
                requestId = REQUEST_ID_1
            }
        )

        assertEquals(ExternalEventStateStatus(ExternalEventStateType.OK, null), updatedExternalEventState.status)
    }

    @ParameterizedTest(name = "processEventReceived updates the state's status to {0} when a {1} response was received")
    @MethodSource("errorResponses")
    fun `processEventReceived updates the state's status when an error response was received`(
        stateType: ExternalEventStateType,
        errorType: ExternalEventResponseErrorType
    ) {
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
        }
        val updatedExternalEventState = externalEventManager.processResponse(
            externalEventState,
            ExternalEventResponse().apply {
                requestId = REQUEST_ID_1
                error = ExternalEventResponseError(errorType, ExceptionEnvelope())
            }
        )

        assertEquals(ExternalEventStateStatus(stateType, ExceptionEnvelope()), updatedExternalEventState.status)
    }

    @Test
    fun `processEventReceived throws an exception if an invalid error type is received`() {
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
        }
        assertThrows<FlowFatalException> {
            externalEventManager.processResponse(
                externalEventState,
                ExternalEventResponse().apply {
                    requestId = REQUEST_ID_1
                    error = ExternalEventResponseError(null, ExceptionEnvelope())
                }
            )
        }
    }

    @Test
    fun `processEventReceived does not update the state when a response with the wrong request id is received`() {
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
        }
        val updatedExternalEventState = externalEventManager.processResponse(
            externalEventState,
            ExternalEventResponse().apply {
                requestId = "wrong request id"
            }
        )

        assertEquals(
            ExternalEventState().apply {
                requestId = REQUEST_ID_1
                status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
            },
            updatedExternalEventState
        )
    }

    @Test
    fun `hasReceivedResponse returns true if the state has a response`() {
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            response = ExternalEventResponse()
        }
        assertTrue(externalEventManager.hasReceivedResponse(externalEventState))
    }

    @Test
    fun `hasReceivedResponse returns false if the state does not have a response`() {
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            response = null
        }
        assertFalse(externalEventManager.hasReceivedResponse(externalEventState))
    }

    @Test
    fun `getReceivedResponse deserializes a string response payload using the string deserializer`() {
        val externalEventResponse = ExternalEventResponse().apply {
            requestId = REQUEST_ID_1
            payload = ByteBuffer.wrap(BYTES)
        }
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            response = externalEventResponse
        }

        whenever(stringDeserializer.deserialize(BYTES)).thenReturn("im a string")

        externalEventManager.getReceivedResponse(externalEventState, String::class.java)
        verify(stringDeserializer).deserialize(BYTES)
        verifyNoInteractions(byteArrayDeserializer)
        verifyNoInteractions(anyDeserializer)
    }

    @Test
    fun `getReceivedResponse deserializes a byte array response payload using the byte array deserializer`() {
        val externalEventResponse = ExternalEventResponse().apply {
            requestId = REQUEST_ID_1
            payload = ByteBuffer.wrap(BYTES)
        }
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            response = externalEventResponse
        }

        whenever(byteArrayDeserializer.deserialize(BYTES)).thenReturn(BYTES)

        externalEventManager.getReceivedResponse(externalEventState, ByteArray::class.java)
        verify(byteArrayDeserializer).deserialize(BYTES)
        verifyNoInteractions(stringDeserializer)
        verifyNoInteractions(anyDeserializer)
    }

    @Test
    fun `getReceivedResponse deserializes an avro response payload using the any deserializer`() {
        val externalEventResponse = ExternalEventResponse().apply {
            requestId = REQUEST_ID_1
            payload = ByteBuffer.wrap(BYTES)
        }
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            response = externalEventResponse
        }

        whenever(anyDeserializer.deserialize(BYTES)).thenReturn(Object())

        externalEventManager.getReceivedResponse(externalEventState, FlowOpsResponse::class.java)
        verify(anyDeserializer).deserialize(BYTES)
        verifyNoInteractions(stringDeserializer)
        verifyNoInteractions(byteArrayDeserializer)
    }

    @Test
    fun `getReceivedResponse throws an IllegalStateException if the state does not have a response`() {
        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            response = null
        }
        assertThrows<IllegalStateException> {
            externalEventManager.getReceivedResponse(externalEventState, FlowOpsResponse::class.java)
        }
    }

    @Test
    fun `getEventToSend returns an external event and updates the state if the state's sendTimestamp is null`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val key = ByteBuffer.wrap(KEY.toByteArray())
        val payload = ByteBuffer.wrap(byteArrayOf(1, 2, 3))

        val externalEvent = ExternalEvent().apply {
            this.topic = TOPIC
            this.key = key
            this.payload = payload
            this.timestamp = now.minusSeconds(10)
        }

        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            eventToSend = externalEvent
            sendTimestamp = null
            status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
        }

        val (updatedExternalEventState, record) = externalEventManager.getEventToSend(
            externalEventState,
            now,
            Duration.ofMillis(0L)
        )

        assertEquals(now, updatedExternalEventState.eventToSend.timestamp)
        assertEquals(now, updatedExternalEventState.sendTimestamp)
        assertEquals(TOPIC, record!!.topic)
        assertEquals(key.array(), record.key)
        assertEquals(payload.array(), record.value)
    }

    @Test
    fun `getEventToSend returns an external event if the event has been sent previously but the window has not expired`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val key = ByteBuffer.wrap(KEY.toByteArray())
        val payload = ByteBuffer.wrap(byteArrayOf(1, 2, 3))

        val externalEvent = ExternalEvent().apply {
            this.topic = TOPIC
            this.key = key
            this.payload = payload
            this.timestamp = now.minusSeconds(10)
        }

        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            eventToSend = externalEvent
            sendTimestamp = now.minusSeconds(10)
            status = ExternalEventStateStatus(ExternalEventStateType.RETRY, ExceptionEnvelope())
            retries = 0
        }

        val (updatedExternalEventState, record) = externalEventManager.getEventToSend(
            externalEventState,
            now,
            Duration.ofSeconds(100L)
        )

        assertEquals(now.minusSeconds(10), updatedExternalEventState.sendTimestamp)
        assertEquals(0, externalEventState.retries)
        assertEquals(now, updatedExternalEventState.eventToSend.timestamp)
        assertEquals(TOPIC, record!!.topic)
        assertEquals(key.array(), record.key)
        assertEquals(payload.array(), record.value)
    }

    @Test
    fun `getEventToSend returns an external event if the event is outside the retry window but has not been resent yet`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val key = ByteBuffer.wrap(KEY.toByteArray())
        val payload = ByteBuffer.wrap(byteArrayOf(1, 2, 3))

        val externalEvent = ExternalEvent().apply {
            this.topic = TOPIC
            this.key = key
            this.payload = payload
            this.timestamp = now.minusSeconds(10)
        }

        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            eventToSend = externalEvent
            sendTimestamp = now.minusSeconds(10)
            status = ExternalEventStateStatus(ExternalEventStateType.RETRY, ExceptionEnvelope())
            retries = 0
        }

        val (updatedExternalEventState, record) = externalEventManager.getEventToSend(
            externalEventState,
            now,
            Duration.ofMillis(100L)
        )

        assertEquals(now.minusSeconds(10), updatedExternalEventState.sendTimestamp)
        assertEquals(1, externalEventState.retries)
        assertEquals(now, updatedExternalEventState.eventToSend.timestamp)
        assertEquals(TOPIC, record!!.topic)
        assertEquals(key.array(), record.key)
        assertEquals(payload.array(), record.value)
    }

    @Test
    fun `getEventToSend throws a fatal exception if the event is outside the retry window and has already been retried`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val key = ByteBuffer.wrap(KEY.toByteArray())
        val payload = ByteBuffer.wrap(byteArrayOf(1, 2, 3))

        val externalEvent = ExternalEvent().apply {
            this.topic = TOPIC
            this.key = key
            this.payload = payload
            this.timestamp = now.minusSeconds(10)
        }

        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            eventToSend = externalEvent
            sendTimestamp = now.minusSeconds(10)
            status = ExternalEventStateStatus(ExternalEventStateType.RETRY, ExceptionEnvelope())
            retries = 1
        }

        assertThrows<FlowFatalException> {
            externalEventManager.getEventToSend(
                externalEventState,
                now,
                Duration.ofMillis(100L)
            )
        }
    }

    @Test
    fun `getEventToSend does not return a record if the status is not OK or RETRY`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val key = ByteBuffer.wrap(KEY.toByteArray())
        val payload = ByteBuffer.wrap(byteArrayOf(1, 2, 3))

        val externalEvent = ExternalEvent().apply {
            this.topic = TOPIC
            this.key = key
            this.payload = payload
            this.timestamp = now.minusSeconds(10)
        }

        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            eventToSend = externalEvent
            sendTimestamp = now.minusSeconds(10)
            status = ExternalEventStateStatus(ExternalEventStateType.PLATFORM_ERROR, ExceptionEnvelope())
            retries = 0
        }

        val (_, record) = externalEventManager.getEventToSend(
            externalEventState,
            now,
            Duration.ofSeconds(100L)
        )

        assertEquals(null, record)
    }

    @Test
    fun `getEventToSend does not return a record if the state is OK and a record has already been sent`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val key = ByteBuffer.wrap(KEY.toByteArray())
        val payload = ByteBuffer.wrap(byteArrayOf(1, 2, 3))

        val externalEvent = ExternalEvent().apply {
            this.topic = TOPIC
            this.key = key
            this.payload = payload
            this.timestamp = now.minusSeconds(10)
        }

        val externalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            eventToSend = externalEvent
            sendTimestamp = now.minusSeconds(10)
            status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
            retries = 0
        }

        val (_, record) = externalEventManager.getEventToSend(
            externalEventState,
            now,
            Duration.ofSeconds(100L)
        )

        assertEquals(null, record)
    }
}