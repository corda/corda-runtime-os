package net.corda.flow.mapper.impl.executor

import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.p2p.app.AppMessage
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class SessionEventExecutorTest {

    private val sessionId = "sessionId"
    private val sessionEventSerializer = mock<CordaAvroSerializer<SessionEvent>>()

    @Test
    fun `Session event executor test outbound data message and non null state`() {
        val bytes = "bytes".toByteArray()
        whenever(sessionEventSerializer.serialize(any())).thenReturn(bytes)
        val payload = buildSessionEvent(MessageDirection.OUTBOUND, sessionId, 1, SessionData(ByteBuffer.wrap(bytes)))

        val result = SessionEventExecutor(sessionId, payload, FlowMapperState(), Instant.now(), sessionEventSerializer).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo(P2P_OUT_TOPIC)
        assertThat(outboundEvent.key).isEqualTo(sessionId)
        assertThat(payload.sessionId).isEqualTo(sessionId)
        assertThat(outboundEvent.value!!::class).isEqualTo(AppMessage::class)
    }

    @Test
    fun `Session event executor test inbound data message and non null state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionData())

        val result = SessionEventExecutor(
            sessionId, payload, FlowMapperState(
                "flowId1", null, FlowMapperStateType.OPEN
            ),
            Instant.now(),
            sessionEventSerializer
        ).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo(FLOW_EVENT_TOPIC)
        assertThat(outboundEvent.key).isEqualTo("flowId1")
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowEvent::class)
        assertThat(payload.sessionId).isEqualTo(sessionId)
    }

    @Test
    fun `Session event with null state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionData())
        val result = SessionEventExecutor(sessionId, payload, null, Instant.now(), sessionEventSerializer).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNull()
        assertThat(outboundEvents.size).isEqualTo(1)

        val outputRecord = outboundEvents.first()
        assertThat(outputRecord.value!!::class.java).isEqualTo(FlowMapperEvent::class.java)
        assertThat(outputRecord.key).isEqualTo(sessionId)
        val flowMapperEvent = outputRecord.value as FlowMapperEvent
        val sessionEvent = flowMapperEvent.payload as SessionEvent
        assertThat(sessionEvent.payload::class.java).isEqualTo(SessionError::class.java)
    }
}
