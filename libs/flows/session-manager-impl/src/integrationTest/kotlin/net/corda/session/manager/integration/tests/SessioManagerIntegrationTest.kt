package net.corda.session.manager.integration.tests

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

class SessionManagerIntegrationTest {

    private companion object {
        private const val testResendWindow = 5000L
        private val testConfig = ConfigFactory.empty()
            .withValue(FlowConfig.SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(testResendWindow))
        private val configFactory = SmartConfigFactory.create(testConfig)
        private val testSmartConfig = configFactory.create(testConfig)
    }

    @Test
    fun `Full Session Send And Receive`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //alice send data
        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        //bob receive data and send ack
        bob.processNextReceivedMessage(sendMessages = true)
        //process ack
        alice.processNextReceivedMessage()

        alice.assertAllMessagesDelivered()
        bob.assertAllMessagesDelivered()

        closeSession(alice, bob)

        alice.assertLastSentSeqNum(3)
        bob.assertLastReceivedSeqNum(3)
        bob.assertLastSentSeqNum(1)
        alice.assertLastReceivedSeqNum(1)
    }


    @Test
    fun testMessageResendWindow() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        val instant = Instant.now()
        //alice send data
        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = false, instant)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(1)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(1)
        alice.sendMessages(instant.plusMillis(testResendWindow))
        assertThat(bob.getInboundMessageSize()).isEqualTo(2)
    }

    @Test
    fun `Simultaneous Close`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //bob and alice send close
        bob.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        alice.assertStatus(SessionStateType.CLOSING)
        bob.assertStatus(SessionStateType.CLOSING)

        //bob receive Close and send ack back aswell as resend close
        bob.processNextReceivedMessage(sendMessages = true)
        //alice receive close and send ack back, also resend close to bob as ack not yet received
        alice.processNextReceivedMessage(sendMessages = true)
        bob.assertStatus(SessionStateType.WAIT_FOR_FINAL_ACK)
        alice.assertStatus(SessionStateType.WAIT_FOR_FINAL_ACK)

        //alice and bob process duplicate closes as well as acks
        alice.processAllReceivedMessages(sendMessages = true)
        alice.assertStatus(SessionStateType.CLOSED)
        bob.processAllReceivedMessages(sendMessages = true)
        bob.assertStatus(SessionStateType.CLOSED)
        alice.processAllReceivedMessages()

        alice.assertAllMessagesDelivered()
        bob.assertAllMessagesDelivered()

        alice.assertLastSentSeqNum(2)
        bob.assertLastReceivedSeqNum(2)
        bob.assertLastSentSeqNum(1)
        alice.assertLastReceivedSeqNum(1)
    }

    @Test
    fun `Out Of Order Random Shuffle`() {
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
    fun `Out Of Order Reversed Inbox`() {
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
    fun `Out Of Order Data With Duplicate Data Resends`() {
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
            sendMessages(Instant.now().plusMillis(testResendWindow))
        }

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
    fun `Out Of Order Close Message With Duplicate Close`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        alice.processNewOutgoingMessage(SessionMessageType.DATA)
        alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        //bob loses data messages
        bob.dropInboundMessage(0)
        alice.sendMessages(Instant.now().plusMillis(testResendWindow)) //Bob inbound queue is now CLOSE, DATA, CLOSE

        bob.processAllReceivedMessages(sendMessages = true)

        //alice process acks for data and close
        alice.processAllReceivedMessages()

        //bob send close to alice
        bob.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        //alice receive close and send ack to bob
        alice.processNextReceivedMessage(sendMessages = true)
        //bob process ack for close
        bob.processNextReceivedMessage()

        alice.assertStatus(SessionStateType.CLOSED)
        bob.assertStatus(SessionStateType.CLOSED)

        alice.assertLastSentSeqNum(3)
        bob.assertLastReceivedSeqNum(3)
        bob.assertLastSentSeqNum(1)
        alice.assertLastReceivedSeqNum(1)
    }
}
