package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionEventExecutorTest {

    @Test
    fun `Session event executor test outbound data message and non null state`() {
        val payload = SessionEvent(MessageDirection.OUTBOUND,1, "sessionId", 1, SessionData(null))

        val result = SessionEventExecutor("sessionId",  payload, FlowMapperState()).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo(P2P_OUT_TOPIC)
        assertThat(outboundEvent.key).isEqualTo("sessionId-INITIATED")
        assertThat(payload.sessionId).isEqualTo("sessionId-INITIATED")
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowMapperEvent::class)
    }

    @Test
    fun `Session event executor test inbound data message and non null state`() {
        val flowKey = FlowKey()
        val payload = SessionEvent(MessageDirection.INBOUND, 1, "sessionId-INITIATED", 1, SessionData(null))

        val result = SessionEventExecutor(
            "sessionId-INITIATED", payload, FlowMapperState(
                flowKey, null, FlowMapperStateType.OPEN
            )
        ).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo(FLOW_EVENT_TOPIC)
        assertThat(outboundEvent.key).isEqualTo(flowKey)
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowEvent::class)
        assertThat(payload.sessionId).isEqualTo("sessionId")
    }

    @Test
    fun `Session event executor test with null state`() {
        val payload = SessionEvent(MessageDirection.OUTBOUND,1, "sessionId", 1, SessionData(null))

        val result = SessionEventExecutor("sessionId",  payload, null).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNull()
        assertThat(outboundEvents).isEmpty()
    }
}
