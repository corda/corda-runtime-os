package net.corda.flow.mapper.impl.executor

import com.typesafe.config.ConfigValueFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.data.p2p.app.AppMessage
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.FlowConfig
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant

class SessionErrorExecutorTest {

    private val sessionId = "sessionId"
    private val flowConfig = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
    private val sessionEventSerializer = mock<CordaAvroSerializer<SessionEvent>>()

    @Test
    fun `Session error event received with null state`() {
        val payload = buildSessionEvent(MessageDirection.INBOUND, sessionId, 1, SessionError())
        val appMessageFactoryCaptor = AppMessageFactoryCaptor(AppMessage())
        val result = SessionErrorExecutor(
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

        Assertions.assertThat(state).isNull()
        Assertions.assertThat(outboundEvents.size).isEqualTo(0)
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