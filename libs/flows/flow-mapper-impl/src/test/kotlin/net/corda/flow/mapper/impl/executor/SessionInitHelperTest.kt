package net.corda.flow.mapper.impl.executor

import com.typesafe.config.ConfigValueFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.p2p.app.AppMessage
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.Schemas
import net.corda.schema.configuration.FlowConfig
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionInitHelperTest {

    private val sessionEventSerializer = mock<CordaAvroSerializer<SessionEvent>>()
    private val flowConfig = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
    private val sessionInitHelper = SessionInitHelper(sessionEventSerializer)

    @Test
    fun `Inbound session init creates new state and forwards to flow event`() {
        val sessionInit = SessionInit("", null, emptyKeyValuePairList(), emptyKeyValuePairList())
        val payload = buildSessionEvent(
            MessageDirection.INBOUND,
            "sessionId-INITIATED",
            1,
            sessionInit,
            contextSessionProps = emptyKeyValuePairList()
        )
        val result = sessionInitHelper.processSessionInit(payload, sessionInit, flowConfig)

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        Assertions.assertThat(state).isNotNull
        Assertions.assertThat(state?.flowId).isNotNull
        Assertions.assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        Assertions.assertThat(state?.expiryTime).isEqualTo(null)

        Assertions.assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        Assertions.assertThat(outboundEvent.topic).isEqualTo(Schemas.Flow.FLOW_EVENT_TOPIC)
        Assertions.assertThat(outboundEvent.key::class).isEqualTo(String::class)
        Assertions.assertThat(outboundEvent.value!!::class).isEqualTo(FlowEvent::class)
        Assertions.assertThat(payload.sessionId).isEqualTo("sessionId-INITIATED")
    }

    @Test
    fun `Outbound session init executes session init helper`() {
        val bytes = "bytes".toByteArray()
        whenever(sessionEventSerializer.serialize(any())).thenReturn(bytes)

        val flowId = "id1"
        val sessionInit = SessionInit("", flowId, emptyKeyValuePairList(), emptyKeyValuePairList())
        val payload =
            buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, sessionInit, contextSessionProps = emptyKeyValuePairList())
        val result = sessionInitHelper.processSessionInit(payload, sessionInit, flowConfig)
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        Assertions.assertThat(state).isNotNull
        Assertions.assertThat(state?.flowId).isEqualTo(flowId)
        Assertions.assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        Assertions.assertThat(state?.expiryTime).isEqualTo(null)

        Assertions.assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        Assertions.assertThat(outboundEvent.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
        Assertions.assertThat(outboundEvent.key).isEqualTo("sessionId")
        Assertions.assertThat(payload.sessionId).isEqualTo("sessionId")
        Assertions.assertThat(outboundEvent.value!!::class).isEqualTo(AppMessage::class)
    }
}