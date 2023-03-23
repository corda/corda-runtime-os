package net.corda.flow.mapper.impl.executor

import com.typesafe.config.ConfigValueFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.p2p.app.AppMessage
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.schema.configuration.FlowConfig.SESSION_P2P_TTL
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionInitExecutorTest {

    private val sessionEventSerializer = mock<CordaAvroSerializer<SessionEvent>>()
    private val flowConfig = SmartConfigImpl.empty().withValue(SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))

    @Test
    fun `Outbound session init creates new state and forwards to P2P`() {
        val bytes = "bytes".toByteArray()
        whenever(sessionEventSerializer.serialize(any())).thenReturn(bytes)

        val flowId = "id1"
        val sessionInit = SessionInit("", flowId, emptyKeyValuePairList(), emptyKeyValuePairList(),emptyKeyValuePairList(), null)
        val payload = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, sessionInit)
        val result =
            SessionInitExecutor("sessionId", payload, sessionInit, null, sessionEventSerializer, flowConfig).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(state?.flowId).isEqualTo(flowId)
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isEqualTo(null)

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo(P2P_OUT_TOPIC)
        assertThat(outboundEvent.key).isEqualTo("sessionId")
        assertThat(payload.sessionId).isEqualTo("sessionId")
        assertThat(outboundEvent.value!!::class).isEqualTo(AppMessage::class)
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
            flowConfig
        ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(state?.flowId).isNotNull
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isEqualTo(null)

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo(FLOW_EVENT_TOPIC)
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
            flowConfig
        ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents).isEmpty()
    }
}
