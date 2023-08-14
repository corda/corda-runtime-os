package net.corda.flow.mapper.impl.executor

import com.typesafe.config.ConfigValueFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
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
import org.mockito.kotlin.whenever
import java.time.Instant

class SessionInitExecutorTest {

    private val sessionEventSerializer = mock<CordaAvroSerializer<SessionEvent>>()
    private val flowConfig = SmartConfigImpl.empty().withValue(SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
    private val record = Record("Topic", "Key", "Value")
    private val recordFactory = mock<RecordFactory>(){
        on { forwardError(any(), any(), any(), any(), any()) } doReturn record
        on { forwardEvent(any(), any(), any(), any()) } doReturn record
        on { getSessionEventOutputTopic(any(), any()) } doReturn "Topic"
    }
    @Test
    fun `Outbound session init creates new state and forwards to P2P`() {
        val bytes = "bytes".toByteArray()
        whenever(sessionEventSerializer.serialize(any())).thenReturn(bytes)

        val flowId = "id1"
        val sessionInit = SessionInit("", flowId, emptyKeyValuePairList(), emptyKeyValuePairList(),emptyKeyValuePairList(), null)
        val payload = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, sessionInit)
        val result =
            SessionInitExecutor(
                "sessionId",
                payload,
                sessionInit,
                null,
                sessionEventSerializer,
                flowConfig,
                recordFactory,
                Instant.now()
                ).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(state?.flowId).isEqualTo(flowId)
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isEqualTo(null)

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo("Topic")
        assertThat(payload.sessionId).isEqualTo("sessionId")
    }

    @Test
    fun `Inbound session init creates new state and forwards to flow event`() {
        val sessionInit = SessionInit("", null, emptyKeyValuePairList(), emptyKeyValuePairList(), emptyKeyValuePairList(), null)
        val payload = buildSessionEvent(MessageDirection.INBOUND, "sessionId-INITIATED", 1, sessionInit)
        val result = SessionInitExecutor(
            "sessionId-INITIATED",
            payload,
            sessionInit,
            null,
            sessionEventSerializer,
            flowConfig,
            recordFactory,
            Instant.now()
            ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(state?.flowId).isNotNull
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isEqualTo(null)

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.key::class).isEqualTo(String::class)
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowEvent::class)
        assertThat(payload.sessionId).isEqualTo("sessionId-INITIATED")
    }

    @Test
    fun `Session init with non null state ignored`() {
        val sessionInit = SessionInit("", null, emptyKeyValuePairList(), emptyKeyValuePairList(), emptyKeyValuePairList(), null)
        val payload = buildSessionEvent(MessageDirection.INBOUND, "", 1, sessionInit)
        val result = SessionInitExecutor(
            "sessionId-INITIATED",
            payload,
            sessionInit,
            FlowMapperState(),
            sessionEventSerializer,
            flowConfig,
            recordFactory,
            Instant.now()
        ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents).isEmpty()
    }

    @Test
    fun `Subsequent OUTBOUND SessionInit messages get passed through if no ACK received from first message`(){
        whenever(sessionEventSerializer.serialize(any())).thenReturn("bytes".toByteArray())
        val retrySessionInit = SessionInit("info", "flow1", emptyKeyValuePairList(), emptyKeyValuePairList(), emptyKeyValuePairList(), null)
        val payload = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, retrySessionInit)

        val flowMapperState = FlowMapperState()
        flowMapperState.status = FlowMapperStateType.OPEN

        val result = SessionInitExecutor(
            "sessionId",
            payload,
            retrySessionInit,
            flowMapperState,
            sessionEventSerializer,
            flowConfig,
            recordFactory,
            Instant.now()
        ).execute()

        assertThat(result.outputEvents).isNotEmpty
        result.outputEvents.forEach {
            assertThat(it.topic).isEqualTo("Topic")
        }
    }

    @Test
    fun `Duplicate INBOUND SessionInit messages are ignored`() {
        val retrySessionInit = SessionInit("info", "1", emptyKeyValuePairList(), emptyKeyValuePairList(), emptyKeyValuePairList(), null)

        val payload = buildSessionEvent(MessageDirection. INBOUND, "sessionId", 1, retrySessionInit)

        val flowMapperState = FlowMapperState()
        flowMapperState.status = FlowMapperStateType.OPEN

        val resultOutbound = SessionInitExecutor(
            "sessionId",
            payload,
            retrySessionInit,
            flowMapperState,
            sessionEventSerializer,
            flowConfig,
            recordFactory,
            Instant.now()
        ).execute()

        assertThat(resultOutbound.outputEvents).isEmpty()
    }
}
