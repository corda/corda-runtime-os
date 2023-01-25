package net.corda.session.manager.integration.interactions

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.state.session.SessionStateType
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.integration.helper.assertStatus
import net.corda.session.manager.integration.helper.initiateNewSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionHeartbeatIntegrationTest {

    private companion object {
        private const val THREE_SECONDS = 3000L
        private const val FIVE_SECONDS = 5000L
        private const val THIRTY_SECONDS = 30000L
        private val testConfig = ConfigFactory.empty()
            .withValue(FlowConfig.SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(FIVE_SECONDS))
            .withValue(FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(THIRTY_SECONDS))
        private val configFactory = SmartConfigFactory.createWithoutSecurityServices()
        private val testSmartConfig = configFactory.create(testConfig)
    }

    @Test
    fun `Check send message respects the resend window`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        var instant = Instant.now()

        assertThat(bob.getInboundMessageSize()).isEqualTo(0)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(0)

        instant = instant.plusMillis(THREE_SECONDS)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(0)

        instant = instant.plusMillis(FIVE_SECONDS)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(1)
        bob.processNextReceivedMessage(true, instant)

        instant = instant.plusMillis(THREE_SECONDS)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(0)
        bob.processNextReceivedMessage(true, instant)

        instant = instant.plusMillis(FIVE_SECONDS)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(1)
        bob.processNextReceivedMessage(true, instant)

        alice.assertStatus(SessionStateType.CONFIRMED)
        bob.assertStatus(SessionStateType.CONFIRMED)

        instant = instant.plusMillis(THIRTY_SECONDS)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(1)
        bob.processNextReceivedMessage(true, instant)

        alice.assertStatus(SessionStateType.ERROR)
        bob.assertStatus(SessionStateType.ERROR)
    }
}
