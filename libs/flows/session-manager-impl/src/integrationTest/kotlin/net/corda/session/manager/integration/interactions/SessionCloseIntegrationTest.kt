package net.corda.session.manager.integration.interactions

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.state.session.SessionStateType
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.helper.assertAllMessagesDelivered
import net.corda.session.manager.integration.helper.assertLastReceivedSeqNum
import net.corda.session.manager.integration.helper.assertLastSentSeqNum
import net.corda.session.manager.integration.helper.assertStatus
import net.corda.session.manager.integration.helper.closeSession
import net.corda.session.manager.integration.helper.initiateNewSession
import org.junit.jupiter.api.Test

class SessionCloseIntegrationTest {

    private companion object {
        private const val THIRTY_SECONDS = 30000L
        private val testConfig = ConfigFactory.empty()
            .withValue(FlowConfig.SESSION_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(THIRTY_SECONDS))
        private val configFactory = SmartConfigFactory.createWithoutSecurityServices()
        private val testSmartConfig = configFactory.create(testConfig)
    }

    @Test
    fun `Full session send and receive with standard close`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //alice sends data
        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        //bob receives data
        bob.processNextReceivedMessage()

        alice.assertAllMessagesDelivered()
        bob.assertAllMessagesDelivered()

        closeSession(alice, bob)

        alice.assertLastSentSeqNum(1)
        bob.assertLastReceivedSeqNum(1)
        bob.assertLastSentSeqNum(1)
        alice.assertLastReceivedSeqNum(1)
    }

    @Test
    fun `Bob tries to close when client has not received all data messages`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)

        //bob loses first data messages
        bob.dropInboundMessage(0)

        //Bob processes out of order data message
        bob.processAllReceivedMessages(sendMessages = true)

        //alice process acks for data and close
        alice.processAllReceivedMessages()

        alice.assertStatus(SessionStateType.CONFIRMED)
        bob.assertStatus(SessionStateType.CONFIRMED)

        //bob send close to alice
        bob.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)

        //alice receive error and ack
        alice.processAllReceivedMessages()

        alice.assertStatus(SessionStateType.ERROR)
        bob.assertStatus(SessionStateType.ERROR)
    }
}
