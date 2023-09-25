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
import net.corda.session.manager.integration.helper.initiateNewSession
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionDataIntegrationTest {

    private companion object {
        private const val THIRTY_SECONDS = 30000L
        private val testConfig = ConfigFactory.empty()
            .withValue(FlowConfig.SESSION_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(THIRTY_SECONDS))
        private val configFactory = SmartConfigFactory.createWithoutSecurityServices()
        private val testSmartConfig = configFactory.create(testConfig)
    }

    @Test
    fun `Out of order random shuffle`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        alice.apply {
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        }

        bob.apply {
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        }

        alice.randomShuffleInboundMessages()
        bob.randomShuffleInboundMessages()

        //process data messages
        alice.processAllReceivedMessages(sendMessages = true)
        //process data messages and acks
        bob.processAllReceivedMessages(sendMessages = true)

        alice.assertAllMessagesDelivered()
        bob.assertAllMessagesDelivered()

        alice.assertLastSentSeqNum(6)
        bob.assertLastReceivedSeqNum(6)
        bob.assertLastSentSeqNum(6)
        alice.assertLastReceivedSeqNum(6)
    }

    @Test
    fun `Out of order reversed inbox`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        alice.apply {
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        }

        bob.apply {
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA)
            processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        }

        alice.reverseInboundMessages()
        bob.reverseInboundMessages()

        //process data messages
        alice.processAllReceivedMessages(sendMessages = true)
        //process data messages and acks
        bob.processAllReceivedMessages(sendMessages = true)

        alice.assertAllMessagesDelivered()
        bob.assertAllMessagesDelivered()

        alice.assertLastSentSeqNum(6)
        bob.assertLastReceivedSeqNum(6)
        bob.assertLastSentSeqNum(6)
        alice.assertLastReceivedSeqNum(6)
    }

    @Test
    fun `Out of order data with duplicate data`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //alice send 2 data
        alice.apply {
            processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
            processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        }

        //bob receive data out of order and send 1 ack back
        bob.processNextReceivedMessage()

        //duplicate data message
        bob.duplicateMessage(0)

        //bob receive duplicate data message
        bob.processAllReceivedMessages()

        alice.assertLastSentSeqNum(2)
        bob.assertLastReceivedSeqNum(2)
        bob.assertLastSentSeqNum(0)
        alice.assertLastReceivedSeqNum(0)
    }

    @Test
    fun `Alice sends data to bob, bob responds with error followed by ack for data`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        val instant = Instant.now()
        //alice send data
        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true, instant)
        //bob send error
        bob.processNewOutgoingMessage(SessionMessageType.ERROR, sendMessages = true, instant)
        //bob process data and send back ack
        bob.processNextReceivedMessage(sendMessages = true)

        //alice receives error and then ack
        alice.processAllReceivedMessages()

        alice.assertStatus(SessionStateType.ERROR)
        bob.assertStatus(SessionStateType.ERROR)
    }
}
