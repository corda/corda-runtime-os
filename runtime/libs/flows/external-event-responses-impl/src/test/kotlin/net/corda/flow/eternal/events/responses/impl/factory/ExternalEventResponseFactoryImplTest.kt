package net.corda.flow.eternal.events.responses.impl.factory

import java.nio.ByteBuffer
import java.time.Instant
import java.time.temporal.ChronoUnit
import net.corda.data.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.flow.external.events.responses.impl.factory.ExternalEventResponseFactoryImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExternalEventResponseFactoryImplTest {

    private companion object {
        val NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val EXTERNAL_EVENT_CONTEXT = ExternalEventContext("request id", "flow id", KeyValuePairList(emptyList()))
        val EXCEPTION = IllegalArgumentException("I'm broke")
        val EXCEPTION_ENVELOPE = ExceptionEnvelope(EXCEPTION::class.java.name, EXCEPTION.message)
    }

    private val serializer = mock<CordaAvroSerializer<Any>>()
    private val clock = mock<Clock>()
    private val externalEventResponseFactory = ExternalEventResponseFactoryImpl(serializer, clock)

    @BeforeEach
    fun beforeEach() {
        whenever(clock.instant()).thenReturn(NOW)
    }

    @Test
    fun `create success response from context and payload`() {
        val payload = FlowOpsResponse()
        val serialized = byteArrayOf(1, 2, 3)

        whenever(serializer.serialize(payload)).thenReturn(serialized)

        val record = externalEventResponseFactory.success(EXTERNAL_EVENT_CONTEXT, payload)
        val flowEvent = record.value!!
        val response = flowEvent.payload as ExternalEventResponse

        assertEquals(Schemas.Flow.FLOW_EVENT_TOPIC, record.topic)
        assertEquals(EXTERNAL_EVENT_CONTEXT.flowId, record.key)
        assertEquals(EXTERNAL_EVENT_CONTEXT.flowId, flowEvent.flowId)
        assertEquals(EXTERNAL_EVENT_CONTEXT.requestId, response.requestId)
        assertEquals(ByteBuffer.wrap(serialized), response.payload)
        assertNull(response.error)
        assertEquals(NOW, response.timestamp)
    }

    @Test
    fun `create success response from request id, flow id and payload`() {
        val payload = FlowOpsResponse()
        val serialized = byteArrayOf(1, 2, 3)

        whenever(serializer.serialize(payload)).thenReturn(serialized)

        val record = externalEventResponseFactory.success(
            EXTERNAL_EVENT_CONTEXT.requestId,
            EXTERNAL_EVENT_CONTEXT.flowId,
            payload
        )
        val flowEvent = record.value!!
        val response = flowEvent.payload as ExternalEventResponse

        assertEquals(Schemas.Flow.FLOW_EVENT_TOPIC, record.topic)
        assertEquals(EXTERNAL_EVENT_CONTEXT.flowId, record.key)
        assertEquals(EXTERNAL_EVENT_CONTEXT.flowId, flowEvent.flowId)
        assertEquals(EXTERNAL_EVENT_CONTEXT.requestId, response.requestId)
        assertEquals(ByteBuffer.wrap(serialized), response.payload)
        assertNull(response.error)
        assertEquals(NOW, response.timestamp)
    }

    @Test
    fun `transient with throwable input`() {
        assertErrorResponse(
            externalEventResponseFactory.transientError(EXTERNAL_EVENT_CONTEXT, EXCEPTION),
            ExternalEventResponseErrorType.TRANSIENT
        )
    }

    @Test
    fun `transient with exception envelope input`() {
        assertErrorResponse(
            externalEventResponseFactory.transientError(EXTERNAL_EVENT_CONTEXT, EXCEPTION_ENVELOPE),
            ExternalEventResponseErrorType.TRANSIENT
        )
    }

    @Test
    fun `platformError with throwable input`() {
        assertErrorResponse(
            externalEventResponseFactory.platformError(EXTERNAL_EVENT_CONTEXT, EXCEPTION),
            ExternalEventResponseErrorType.PLATFORM
        )
    }

    @Test
    fun `platformError with exception envelope input`() {
        assertErrorResponse(
            externalEventResponseFactory.platformError(EXTERNAL_EVENT_CONTEXT, EXCEPTION_ENVELOPE),
            ExternalEventResponseErrorType.PLATFORM
        )
    }

    @Test
    fun `fatalError with throwable input`() {
        assertErrorResponse(
            externalEventResponseFactory.fatalError(EXTERNAL_EVENT_CONTEXT, EXCEPTION),
            ExternalEventResponseErrorType.FATAL
        )
    }

    @Test
    fun `fatalError with exception envelope input`() {
        assertErrorResponse(
            externalEventResponseFactory.fatalError(EXTERNAL_EVENT_CONTEXT, EXCEPTION_ENVELOPE),
            ExternalEventResponseErrorType.FATAL
        )
    }

    private fun assertErrorResponse(record: Record<String, FlowEvent>, errorType: ExternalEventResponseErrorType) {
        val flowEvent = record.value!!
        val response = flowEvent.payload as ExternalEventResponse

        assertEquals(Schemas.Flow.FLOW_EVENT_TOPIC, record.topic)
        assertEquals(EXTERNAL_EVENT_CONTEXT.flowId, record.key)
        assertEquals(EXTERNAL_EVENT_CONTEXT.flowId, flowEvent.flowId)
        assertEquals(EXTERNAL_EVENT_CONTEXT.requestId, response.requestId)
        assertNull(response.payload)
        assertEquals(errorType, response.error.errorType)
        assertEquals(EXCEPTION_ENVELOPE, response.error.exception)
        assertEquals(NOW, response.timestamp)
    }
}
