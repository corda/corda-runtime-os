package net.corda.session.manager.integration.interactions

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.state.session.SessionStateType
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigFactoryFactory
import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.SessionPartyFactory
import net.corda.session.manager.integration.helper.assertAllMessagesDelivered
import net.corda.session.manager.integration.helper.assertStatus
import net.corda.session.manager.integration.helper.initiateNewSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionInitiationIntegrationTest {

    private companion object {
        private const val FIVE_SECONDS = 5000L
        private const val THIRTY_SECONDS = 30000L
        private val testConfig = ConfigFactory.empty()
            .withValue(FlowConfig.SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(FIVE_SECONDS))
            .withValue(FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(THIRTY_SECONDS))
        private val configFactory = SmartConfigFactoryFactory.createWithoutSecurityServices()
        private val testSmartConfig = configFactory.create(testConfig)
    }

    @Test
    fun `Alice initiate session with Bob, Alice tries to send duplicate session init`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        alice.processNewOutgoingMessage(SessionMessageType.INIT, sendMessages = true)
        bob.processNextReceivedMessage(sendMessages = true)

        //duplicate is never sent
        assertThat(alice.getInboundMessageSize()).isEqualTo(0)

        alice.assertAllMessagesDelivered()
        bob.assertAllMessagesDelivered()
    }

    @Test
    fun `Alice sends session data before session is confirmed`() {
        val (alice, bob) = SessionPartyFactory().createSessionParties(testSmartConfig)

        //send init
        alice.processNewOutgoingMessage(SessionMessageType.INIT, sendMessages = true)
        alice.assertStatus(SessionStateType.CREATED)

        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        alice.assertStatus(SessionStateType.CREATED)

        //bob process init and confirm session
        bob.processNextReceivedMessage(sendMessages = true)
        bob.assertStatus(SessionStateType.CONFIRMED)

        //alice receive ack for session init
        alice.processNextReceivedMessage(sendMessages = true)
        alice.assertStatus(SessionStateType.CONFIRMED)

        //bob process message
        bob.processNextReceivedMessage()
        bob.assertStatus(SessionStateType.CONFIRMED)
    }

    @Test
    fun `Alice sends Init, Bob initially confirms and then sends error, duplicate init arrives to bob`() {
        val (alice, bob) = SessionPartyFactory().createSessionParties(testSmartConfig)

        //send init
        alice.processNewOutgoingMessage(SessionMessageType.INIT, sendMessages = true)
        alice.assertStatus(SessionStateType.CREATED)

        //duplicate init
        bob.duplicateMessage(0)

        //bob process init and confirm session
        bob.processNextReceivedMessage(sendMessages = true)
        bob.assertStatus(SessionStateType.CONFIRMED)

        bob.processNewOutgoingMessage(SessionMessageType.ERROR, sendMessages = true)
        bob.assertStatus(SessionStateType.ERROR)

        bob.processNextReceivedMessage(sendMessages = true)
        bob.assertStatus(SessionStateType.ERROR)

        //alice receive ack, error and ack
        alice.processNextReceivedMessage(sendMessages = true)
        alice.assertStatus(SessionStateType.CONFIRMED)

        alice.processNextReceivedMessage(sendMessages = true)
        alice.assertStatus(SessionStateType.ERROR)

        alice.processNextReceivedMessage(sendMessages = true)
        alice.assertStatus(SessionStateType.ERROR)
    }

    @Test
    fun `Alice sends Init, Bob responds with error`() {
        val (alice, bob) = SessionPartyFactory().createSessionParties(testSmartConfig)

        //send init
        alice.processNewOutgoingMessage(SessionMessageType.INIT, sendMessages = true)
        alice.assertStatus(SessionStateType.CREATED)

        bob.processNewOutgoingMessage(SessionMessageType.ERROR, sendMessages = true)
        bob.assertStatus(SessionStateType.ERROR)

        alice.processNextReceivedMessage(sendMessages = true)
        alice.assertStatus(SessionStateType.ERROR)

    }

    @Test
    fun `Alice initiates session with Bob, then sends error and receives ack for init`() {
        val (alice, bob) = SessionPartyFactory().createSessionParties(testSmartConfig)

        //send init
        alice.processNewOutgoingMessage(SessionMessageType.INIT, sendMessages = true)
        alice.assertStatus(SessionStateType.CREATED)

        alice.processNewOutgoingMessage(SessionMessageType.ERROR, sendMessages = true)
        alice.assertStatus(SessionStateType.ERROR)

        //bob process init and confirm session
        bob.processNextReceivedMessage(sendMessages = true)
        bob.assertStatus(SessionStateType.CONFIRMED)

        //alice receive ack for session init and send error back
        alice.processNextReceivedMessage(sendMessages = true)
        alice.assertStatus(SessionStateType.ERROR)

        //bob process error
        bob.processNextReceivedMessage()
        bob.assertStatus(SessionStateType.ERROR)
    }
}
