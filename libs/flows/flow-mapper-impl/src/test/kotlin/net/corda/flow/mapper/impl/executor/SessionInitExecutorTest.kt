package net.corda.flow.mapper.impl.executor

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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionInitExecutorTest {

    @Test
    fun `Outbound session init creates new state and forwards to P2P`() {
        val holdingIdentity = HoldingIdentity()
        val flowKey = FlowKey("", holdingIdentity)
        val sessionInit = SessionInit("", "", flowKey, holdingIdentity, holdingIdentity, null)
        val payload = SessionEvent(MessageDirection.OUTBOUND, Instant.now(), "sessionId", 1, sessionInit)

        val result = SessionInitExecutor("sessionId",  payload, sessionInit, null).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(state?.flowKey).isEqualTo(flowKey)
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isEqualTo(null)

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo(P2P_OUT_TOPIC)
        assertThat(outboundEvent.key).isEqualTo("sessionId-INITIATED")
        assertThat(payload.sessionId).isEqualTo("sessionId-INITIATED")
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowMapperEvent::class)
    }

    @Test
    fun `Inbound session init creates new state and forwards to flow event`() {
        val holdingIdentity = HoldingIdentity()
        val sessionInit = SessionInit("", "", null, holdingIdentity, holdingIdentity, null)
        val payload = SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId-INITIATED", 1, sessionInit)
        val result = SessionInitExecutor("sessionId-INITIATED", payload, sessionInit, null).execute()

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
        assertThat(payload.sessionId).isEqualTo("sessionId")
    }

    @Test
    fun `Session init with non null state ignored`() {
        val holdingIdentity = HoldingIdentity()
        val sessionInit = SessionInit("", "", null, holdingIdentity, holdingIdentity, null)
        val payload = SessionEvent(MessageDirection.INBOUND, Instant.now(), "", 1, sessionInit)
        val result = SessionInitExecutor("sessionId-INITIATED", payload, sessionInit, FlowMapperState()).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents).isEmpty()
    }
}
