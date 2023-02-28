package net.corda.flow.mapper.impl.executor

import com.typesafe.config.ConfigValueFactory
import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.data.p2p.app.AppMessage
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.schema.configuration.FlowConfig.SESSION_P2P_TTL
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionEventExecutorTest {

    private val sessionId = "sessionId"
    private val flowConfig = SmartConfigImpl.empty().withValue(SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
    private val sessionEventSerializer = mock<CordaAvroSerializer<SessionEvent>>()

    @Test
    fun `Session event executor test outbound data message and non null state`() {
        val bytes = "bytes".toByteArray()
        whenever(sessionEventSerializer.serialize(any())).thenReturn(bytes)
        val payload = buildSessionEvent(MessageDirection.OUTBOUND, sessionId, 1, SessionData(ByteBuffer.wrap(bytes)))

        val appMessageFactoryCaptor = AppMessageFactoryCaptor(AppMessage())

        val result = SessionEventExecutor(
            sessionId,
            payload,
            FlowMapperState(),
            Instant.now(),
            sessionEventSerializer,
            appMessageFactoryCaptor::generateAppMessage,
            flowConfig
        ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo(P2P_OUT_TOPIC)
        assertThat(outboundEvent.key).isEqualTo(sessionId)
        assertThat(payload.sessionId).isEqualTo(sessionId)
        assertThat(outboundEvent.value).isEqualTo(appMessageFactoryCaptor.appMessage)
        assertThat(appMessageFactoryCaptor.sessionEvent).isEqualTo(payload)
        assertThat(appMessageFactoryCaptor.sessionEventSerializer).isEqualTo(sessionEventSerializer)
    }

    @Test
    fun `Session event executor test inbound data message and non null state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionData())
        val appMessageFactoryCaptor = AppMessageFactoryCaptor(AppMessage())

        val result = SessionEventExecutor(
            sessionId, payload,
            FlowMapperState(
                "flowId1", null, FlowMapperStateType.OPEN
            ),
            Instant.now(),
            sessionEventSerializer,
            appMessageFactoryCaptor::generateAppMessage,
            flowConfig
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
    fun `Session event received with null state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionData())
        val appMessageFactoryCaptor = AppMessageFactoryCaptor(AppMessage())
        val result = SessionEventExecutor(
            sessionId,
            payload,
            null,
            Instant.now(),
            sessionEventSerializer,
            appMessageFactoryCaptor::generateAppMessage,
            flowConfig
        ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNull()
        assertThat(outboundEvents.size).isEqualTo(1)

        val outputRecord = outboundEvents.first()
        assertThat(outputRecord.value).isEqualTo(appMessageFactoryCaptor.appMessage)
        assertThat(appMessageFactoryCaptor.sessionEvent!!.sessionId).isEqualTo(sessionId)
        assertThat(appMessageFactoryCaptor.sessionEvent!!.messageDirection).isEqualTo(MessageDirection.OUTBOUND)
        assertThat(appMessageFactoryCaptor.sessionEvent!!.initiatingIdentity).isEqualTo(payload.initiatingIdentity)
        assertThat(appMessageFactoryCaptor.sessionEvent!!.initiatedIdentity).isEqualTo(payload.initiatedIdentity)
        assertThat(appMessageFactoryCaptor.sessionEvent!!.receivedSequenceNum).isEqualTo(0)
        assertThat(appMessageFactoryCaptor.sessionEvent!!.outOfOrderSequenceNums).isEmpty()
        assertThat(appMessageFactoryCaptor.sessionEvent!!.payload::class.java).isEqualTo(SessionError::class.java)
        val error = appMessageFactoryCaptor.sessionEvent!!.payload as SessionError
        assertThat(error.errorMessage.errorType).isEqualTo("FlowMapper-SessionExpired")
        assertThat(error.errorMessage.errorMessage)
            .isEqualTo("Tried to process session event for expired session with sessionId $sessionId")

        assertThat(appMessageFactoryCaptor.sessionEventSerializer).isEqualTo(sessionEventSerializer)
    }

    @Test
    fun `Session error event received with null state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionError())
        val appMessageFactoryCaptor = AppMessageFactoryCaptor(AppMessage())
        val result = SessionEventExecutor(
            sessionId,
            payload,
            null,
            Instant.now(),
            sessionEventSerializer,
            appMessageFactoryCaptor::generateAppMessage,
            flowConfig
        ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNull()
        assertThat(outboundEvents.size).isEqualTo(0)
    }

    class AppMessageFactoryCaptor(val appMessage: AppMessage) {

        var flowConfig: SmartConfig? = null
        var sessionEvent: SessionEvent? = null
        var sessionEventSerializer: CordaAvroSerializer<SessionEvent>? = null

        fun generateAppMessage(
            sessionEvent: SessionEvent,
            sessionEventSerializer: CordaAvroSerializer<SessionEvent>,
            flowConfig: SmartConfig
        ): AppMessage {
            this.sessionEvent = sessionEvent
            this.sessionEventSerializer = sessionEventSerializer
            this.flowConfig = flowConfig

            return appMessage
        }
    }
}
