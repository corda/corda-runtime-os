package net.corda.flow.mapper.impl.executor

import com.typesafe.config.ConfigValueFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.FlowConfig
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionInitProcessorTest {

    private val recordFactory = object : RecordFactory {
        override fun forwardEvent(
            sourceEvent: SessionEvent,
            instant: Instant,
            flowConfig: SmartConfig,
            flowId: String
        ): Record<*, *> {
            return if (sourceEvent.messageDirection == MessageDirection.INBOUND) {
                Record(Schemas.Flow.FLOW_EVENT_TOPIC, flowId, FlowEvent(flowId, sourceEvent))
            } else {
                Record(Schemas.P2P.P2P_OUT_TOPIC, "sessionId", "")
            }
        }

        override fun forwardError(
            sourceEvent: SessionEvent,
            exceptionEnvelope: ExceptionEnvelope,
            instant: Instant,
            flowConfig: SmartConfig,
            flowId: String
        ): Record<*, *> {
            TODO("Not yet implemented")
        }

        override fun sendBackError(
            sourceEvent: SessionEvent,
            exceptionEnvelope: ExceptionEnvelope,
            instant: Instant,
            flowConfig: SmartConfig
        ): Record<*, *> {
            TODO("Not yet implemented")
        }
    }
    private val flowConfig = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
    private val sessionInitProcessor = SessionInitProcessor(recordFactory)

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
        val result = sessionInitProcessor.processSessionInit(payload, sessionInit, flowConfig, Instant.now())

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

        val flowId = "id1"
        val sessionInit = SessionInit("", flowId, emptyKeyValuePairList(), emptyKeyValuePairList())
        val payload =
            buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, sessionInit, contextSessionProps = emptyKeyValuePairList())
        val result = sessionInitProcessor.processSessionInit(payload, sessionInit, flowConfig, Instant.now())
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        Assertions.assertThat(state).isNotNull
        Assertions.assertThat(state?.flowId).isEqualTo(flowId)
        Assertions.assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        Assertions.assertThat(state?.expiryTime).isEqualTo(null)

        Assertions.assertThat(outboundEvents.size).isEqualTo(1)
    }
}