package net.corda.flow.mapper.impl.executor

import com.typesafe.config.ConfigValueFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig.SESSION_P2P_TTL
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.time.Instant

class SessionEventExecutorTest {

    private val sessionId = "sessionId"
    private val flowConfig = SmartConfigImpl.empty().withValue(SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
    private val sessionEventSerializer = mock<CordaAvroSerializer<SessionEvent>>()
    private val record = Record("Topic", "Key", "Value")
    private val sendBackRecord = Record("Topic", "Key", "Value2")
    private val recordFactory = mock<RecordFactory>{
        on { forwardError(any(), any(), any(), any(), any()) } doReturn record
        on { forwardEvent(any(), any(), any(), any()) } doReturn record
        on { sendBackError(any(), any(), any(), any()) } doReturn sendBackRecord
    }
    private val sessionInitProcessor = mock<SessionInitProcessor>()

    @Test
    fun `Session event executor test outbound data message and non null state`() {
        val bytes = "bytes".toByteArray()
        whenever(sessionEventSerializer.serialize(any())).thenReturn(bytes)
        val payload = buildSessionEvent(
            MessageDirection.OUTBOUND,
            sessionId,
            1,
            SessionData(ByteBuffer.wrap(bytes), null),
            contextSessionProps = emptyKeyValuePairList()
        )

        val result = SessionEventExecutor(
            sessionId,
            payload,
            FlowMapperState(
                "flowId1", null, FlowMapperStateType.OPEN
            ),
            flowConfig,
            recordFactory,
            Instant.now(),
            sessionInitProcessor
            ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo("Topic")
        assertThat(outboundEvent.key).isEqualTo("Key")
        assertThat(payload.sessionId).isEqualTo(sessionId)
        assertThat(outboundEvent.value).isEqualTo("Value")
    }

    @Test
    fun `Session event executor test inbound data message and non null state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionData())

        val result = SessionEventExecutor(
            sessionId, payload,
            FlowMapperState(
                "flowId1", null, FlowMapperStateType.OPEN
            ),
            flowConfig,
            recordFactory,
            Instant.now(),
            sessionInitProcessor
            ).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent).isEqualTo(record)
        assertThat(payload.sessionId).isEqualTo(sessionId)
    }

    @Test
    fun `Session event received with null state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionData())
        val result = SessionEventExecutor(
            sessionId,
            payload,
            null,
            flowConfig,
            recordFactory,
            Instant.now(),
            sessionInitProcessor
            ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNull()
        assertThat(outboundEvents.size).isEqualTo(1)

        val outputRecord = outboundEvents.first()
        assertThat(outputRecord.value).isEqualTo("Value2")
    }

    @Test
    fun `Session event received with CLOSING state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionClose())

        val result = SessionEventExecutor(
            sessionId, payload,
            FlowMapperState(
                "flowId1", null, FlowMapperStateType.CLOSING
            ),
            flowConfig,
            recordFactory,
            Instant.now(),
            sessionInitProcessor
            ).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state?.status).isEqualTo(FlowMapperStateType.CLOSING)
        assertThat(outboundEvents.size).isEqualTo(0)
    }

    @Test
    fun `Session event received with OPEN state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionEvent())

        val result = SessionEventExecutor(
            sessionId, payload,
            FlowMapperState(
                "flowId1", null, FlowMapperStateType.OPEN
            ),
            flowConfig,
            recordFactory,
            Instant.now(),
            sessionInitProcessor
            ).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent).isEqualTo(record)
        assertThat(payload.sessionId).isEqualTo(sessionId)
    }

    @Test
    fun `Session Data with null state and init info`() {
        val payload =
            buildSessionEvent(MessageDirection.INBOUND, sessionId, 1,  SessionData(ByteBuffer.allocate(1), SessionInit()))

        SessionEventExecutor(
            sessionId,
            payload,
            null,
            flowConfig,
            recordFactory,
            Instant.now(),
            sessionInitProcessor
        ).execute()
        verify(sessionInitProcessor, times(1)).processSessionInit(any(), any(), any(), any())
    }

    @Test
    fun `Session data with null state and null session init, when record factory throws returns no records`() {
        val payload =
            buildSessionEvent(MessageDirection.OUTBOUND, sessionId, 1, SessionData(ByteBuffer.allocate(1), null))
        whenever(recordFactory.sendBackError(any(), any(), any(), any())).thenThrow(IllegalArgumentException())
        val output = SessionEventExecutor(
            sessionId,
            payload,
            null,
            flowConfig,
            recordFactory,
            Instant.now(),
            sessionInitProcessor
        ).execute()
        verify(sessionInitProcessor, times(0)).processSessionInit(any(), any(), any(), any())
        assertThat(output.outputEvents.size).isEqualTo(0)
    }

}
