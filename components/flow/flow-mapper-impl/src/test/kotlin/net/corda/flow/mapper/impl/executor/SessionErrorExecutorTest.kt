package net.corda.flow.mapper.impl.executor

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

class SessionErrorExecutorTest {

    private val sessionId = "sessionId"
    private val flowConfig = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
    private val record = Record("Topic", "Key", "Value")
    private val recordFactory = mock<RecordFactory>(){
        on { forwardError(any(), any(), any(), any(), any()) } doReturn record
        on {getSessionEventOutputTopic(any(), any())} doReturn "Topic"
    }

    @Test
    fun `Session error event received with null state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionError())
        val result = SessionErrorExecutor(
            sessionId,
            payload,
            null,
            flowConfig,
            recordFactory,
            Instant.now(),
            ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        Assertions.assertThat(state).isNull()
        Assertions.assertThat(outboundEvents.size).isEqualTo(0)
    }

    @Test
    fun `Session error received with CLOSING state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionError())

        val result = SessionErrorExecutor(
            sessionId, payload, FlowMapperState(
                "flowId1", null, FlowMapperStateType.CLOSING
            ),
            flowConfig,
            recordFactory,
            Instant.now(),
        ).execute()
        val outboundEvents = result.outputEvents

        val state = result.flowMapperState
        Assertions.assertThat(state?.status).isEqualTo(FlowMapperStateType.ERROR)
        Assertions.assertThat(outboundEvents.size).isEqualTo(0)
    }

    @Test
    fun `Session error received with ERROR state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionError())

        val result = SessionErrorExecutor(
            sessionId, payload, FlowMapperState(
                "flowId1", null, FlowMapperStateType.ERROR
            ),
            flowConfig,
            recordFactory,
            Instant.now()
        ).execute()
        val outboundEvents = result.outputEvents

        val state = result.flowMapperState
        Assertions.assertThat(state?.status).isEqualTo(FlowMapperStateType.ERROR)
        Assertions.assertThat(outboundEvents.size).isEqualTo(0)
    }

    @Test
    fun `Session error received with OPEN state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionError())

        val result = SessionErrorExecutor(
            sessionId, payload, FlowMapperState(
                "flowId1", null, FlowMapperStateType.OPEN
            ),
            flowConfig,
            recordFactory,
            Instant.now()
            ).execute()
        val outboundEvents = result.outputEvents
        val state = result.flowMapperState
        Assertions.assertThat(state?.status).isEqualTo(FlowMapperStateType.ERROR)

        Assertions.assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        Assertions.assertThat(outboundEvent.topic).isEqualTo("Topic")
        Assertions.assertThat(outboundEvent.key).isEqualTo("flowId1")
        Assertions.assertThat(outboundEvent.value!!::class).isEqualTo(FlowEvent::class)
        Assertions.assertThat(payload.sessionId).isEqualTo(sessionId)
    }
}