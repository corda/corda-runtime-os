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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionDataIntegrationTest {

    private companion object {
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

        val instant = Instant.now()
        //alice send data
        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = false, instant)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(1)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(1)
        alice.sendMessages(instant.plusMillis(FIVE_SECONDS))
        assertThat(bob.getInboundMessageSize()).isEqualTo(2)
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
        //process acks
        alice.processAllReceivedMessages()

        alice.assertAllMessagesDelivered()
        bob.assertAllMessagesDelivered()

        closeSession(alice, bob)

        alice.assertLastSentSeqNum(8)
        bob.assertLastReceivedSeqNum(8)
        bob.assertLastSentSeqNum(7)
        alice.assertLastReceivedSeqNum(7)
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
        //process acks
        alice.processAllReceivedMessages()

        alice.assertAllMessagesDelivered()
        bob.assertAllMessagesDelivered()

        closeSession(alice, bob)

        alice.assertLastSentSeqNum(8)
        bob.assertLastReceivedSeqNum(8)
        bob.assertLastSentSeqNum(7)
        alice.assertLastReceivedSeqNum(7)
    }

    @Test
    fun `Out of order data with duplicate data resends`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //alice send 2 data
        alice.apply {
            processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
            processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        }

        //bob receive data out of order and send 1 ack back
        bob.apply {
            dropNextInboundMessage()
            processNextReceivedMessage(sendMessages = true)
        }

        alice.apply {
            //process 1 ack
            processNextReceivedMessage()
            //send close and RESEND data
            processNewOutgoingMessage(SessionMessageType.CLOSE)
            sendMessages(Instant.now().plusMillis(FIVE_SECONDS))
        }

        //duplicate resent data message
        bob.duplicateMessage(0)

        //bob receive duplicate data message + close
        bob.processAllReceivedMessages(sendMessages = true)
        //alice process acks for data and close
        alice.processAllReceivedMessages()
        //bob send close to alice
        bob.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        //alice receive close and send ack to bob
        alice.processNextReceivedMessage(sendMessages = true)
        //bob process ack
        bob.processNextReceivedMessage()

        alice.assertStatus(SessionStateType.CLOSED)
        bob.assertStatus(SessionStateType.CLOSED)

        alice.assertLastSentSeqNum(4)
        bob.assertLastReceivedSeqNum(4)
        bob.assertLastSentSeqNum(1)
        alice.assertLastReceivedSeqNum(1)
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
