package net.corda.flow.mapper.impl.executor

import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SessionInitExecutorTest {

    private val flowMapperSerializer = mock<CordaAvroSerializer<SessionEvent>>()

    @Test
    fun `Outbound session init creates new state and forwards to P2P`() {
        val holdingIdentity = HoldingIdentity()
        val flowKey = FlowKey("", holdingIdentity)
        val sessionInit = SessionInit("", "", flowKey, null)
        val payload = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, sessionInit)
        val result = SessionInitExecutor("sessionId", payload, sessionInit, null, flowMapperSerializer).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(state?.flowKey).isEqualTo(flowKey)
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isEqualTo(null)

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo(P2P_OUT_TOPIC)
        assertThat(outboundEvent.key).isEqualTo("sessionId")
        assertThat(payload.sessionId).isEqualTo("sessionId")
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowMapperEvent::class)
    }

    @Test
    fun `Inbound session init creates new state and forwards to flow event`() {
        val sessionInit = SessionInit("", "", null, null)
        val payload = buildSessionEvent(MessageDirection.INBOUND, "sessionId-INITIATED", 1, sessionInit)
        val result = SessionInitExecutor("sessionId-INITIATED", payload, sessionInit, null, flowMapperSerializer).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(state?.flowKey).isNotNull
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isEqualTo(null)

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo(FLOW_EVENT_TOPIC)
        assertThat(outboundEvent.key::class).isEqualTo(FlowKey::class)
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowEvent::class)
        assertThat(payload.sessionId).isEqualTo("sessionId-INITIATED")
    }

    @Test
    fun `Session init with non null state ignored`() {
        val sessionInit = SessionInit("", "", null, null)
        val payload = buildSessionEvent(MessageDirection.INBOUND, "", 1, sessionInit)
        val result = SessionInitExecutor("sessionId-INITIATED", payload, sessionInit, FlowMapperState(), flowMapperSerializer).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents).isEmpty()
    }
}
