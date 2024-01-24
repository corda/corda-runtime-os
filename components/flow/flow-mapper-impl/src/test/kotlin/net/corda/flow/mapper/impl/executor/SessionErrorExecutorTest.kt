package net.corda.flow.mapper.impl.executor

import com.typesafe.config.ConfigValueFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

class SessionErrorExecutorTest {

    private val sessionId = "sessionId"
    private val flowConfig = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
    private val record = Record("Topic", "Key", "Value")
    private val recordFactory = mock<RecordFactory> {
        on { forwardError(any(), any(), any(), any(), any()) } doReturn record
    }

    private val exceptionEnvelope = ExceptionEnvelope("type", "message")
    private val sessionError = SessionError(exceptionEnvelope)

    @Test
    fun `Session error event received with null state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, sessionError)
        val result = SessionErrorExecutor(
            sessionId,
            payload,
            sessionError,
            null,
            flowConfig,
            recordFactory,
            Instant.now(),
            ).execute()

        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNull()
        assertThat(outboundEvents.size).isEqualTo(0)
    }

    @Test
    fun `Session error received with CLOSING state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, sessionError)

        val result = SessionErrorExecutor(
            sessionId, payload,
            sessionError,
            FlowMapperState(
                "flowId1", null, FlowMapperStateType.CLOSING
            ),
            flowConfig,
            recordFactory,
            Instant.now(),
        ).execute()
        val outboundEvents = result.outputEvents

        val state = result.flowMapperState
        assertThat(state?.status).isEqualTo(FlowMapperStateType.ERROR)
        assertThat(outboundEvents.size).isEqualTo(0)
    }

    @Test
    fun `Session error received with ERROR state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, sessionError)

        val result = SessionErrorExecutor(
            sessionId, payload,
            sessionError,
            FlowMapperState(
                "flowId1", null, FlowMapperStateType.ERROR
            ),
            flowConfig,
            recordFactory,
            Instant.now()
        ).execute()
        val outboundEvents = result.outputEvents

        val state = result.flowMapperState
        assertThat(state?.status).isEqualTo(FlowMapperStateType.ERROR)
        assertThat(outboundEvents.size).isEqualTo(0)
    }

    @Test
    fun `Session error received with OPEN state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, sessionError)

        val result = SessionErrorExecutor(
            sessionId, payload,
            sessionError,
            FlowMapperState(
                "flowId1", null, FlowMapperStateType.OPEN
            ),
            flowConfig,
            recordFactory,
            Instant.now()
        ).execute()
        val outboundEvents = result.outputEvents
        val state = result.flowMapperState
        assertThat(state?.status).isEqualTo(FlowMapperStateType.ERROR)

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent).isEqualTo(record)
        assertThat(payload.sessionId).isEqualTo(sessionId)
    }
}