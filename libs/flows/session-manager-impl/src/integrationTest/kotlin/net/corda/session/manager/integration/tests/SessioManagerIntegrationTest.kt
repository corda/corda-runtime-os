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
            .withValue("${FlowConfig.SESSION_MESSAGE_RESEND_WINDOW}", ConfigValueFactory.fromAnyRef(testResendWindow))
        private val configFactory = SmartConfigFactory.create(testConfig)
        private val testSmartConfig = configFactory.create(testConfig)
    }

    @Test
    fun testFullSessionSendAndReceive() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //alice send data
        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        //bob receive data and send ack
        bob.processNextReceivedMessage(sendMessages = true)
        //process ack
        alice.processNextReceivedMessage()

        assertAllMessagesDelivered(alice)
        assertAllMessagesDelivered(bob)

        closeSession(alice, bob)

        assertLastSentSeqNum(alice, 3)
        assertLastReceivedSeqNum(bob, 3)
        assertLastSentSeqNum(bob, 1)
        assertLastReceivedSeqNum(alice, 1)
    }


    @Test
    fun testMessageResendWindow() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        val instant = Instant.now()
        //alice send data
        alice.processNewOutgoingMessage(SessionMessageType.DATA)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(1)
        alice.sendMessages(instant)
        assertThat(bob.getInboundMessageSize()).isEqualTo(1)
        alice.sendMessages(instant.plusMillis(testResendWindow))
        assertThat(bob.getInboundMessageSize()).isEqualTo(2)
    }

    @Test
    fun testSimultaneousClose() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //bob and alice send close
        bob.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        assertStatus(alice, SessionStateType.CLOSING)
        assertStatus(bob, SessionStateType.CLOSING)

        //bob receive Close and send ack back aswell as resend close
        bob.processNextReceivedMessage(sendMessages = true)
        //alice receive close and send ack back, also resend close to bob as ack not yet received
        alice.processNextReceivedMessage(sendMessages = true)
        assertStatus(bob, SessionStateType.WAIT_FOR_FINAL_ACK)
        assertStatus(alice, SessionStateType.WAIT_FOR_FINAL_ACK)

        //alice and bob process duplicate closes as well as acks
        alice.processAllReceivedMessages(sendMessages = true)
        assertStatus(alice, SessionStateType.CLOSED)
        bob.processAllReceivedMessages(sendMessages = true)
        assertStatus(bob, SessionStateType.CLOSED)
        alice.processAllReceivedMessages()

        assertAllMessagesDelivered(alice)
        assertAllMessagesDelivered(bob)

        assertLastSentSeqNum(alice, 2)
        assertLastReceivedSeqNum(bob, 2)
        assertLastSentSeqNum(bob, 1)
        assertLastReceivedSeqNum(alice, 1)
    }

    @Test
    fun testOutOfOrderRandomShuffle() {
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

        assertAllMessagesDelivered(alice)
        assertAllMessagesDelivered(bob)

        closeSession(alice, bob)

        assertLastSentSeqNum(alice, 8)
        assertLastReceivedSeqNum(bob, 8)
        assertLastSentSeqNum(bob, 7)
        assertLastReceivedSeqNum(alice, 7)
    }

    @Test
    fun testOutOfOrderReversedInbox() {
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

        assertAllMessagesDelivered(alice)
        assertAllMessagesDelivered(bob)

        closeSession(alice, bob)

        assertLastSentSeqNum(alice, 8)
        assertLastReceivedSeqNum(bob, 8)
        assertLastSentSeqNum(bob, 7)
        assertLastReceivedSeqNum(alice, 7)
    }

    @Test
    fun testOutOfOrderDataWithDuplicateDataResends() {
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

        assertStatus(alice, SessionStateType.CLOSED)
        assertStatus(bob, SessionStateType.CLOSED)

        assertLastSentSeqNum(alice, 4)
        assertLastReceivedSeqNum(bob, 4)
        assertLastSentSeqNum(bob, 1)
        assertLastReceivedSeqNum(alice, 1)
    }

    @Test
    fun testOutOfOrderCloseMessageWithDuplicateClose() {
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

        assertStatus(alice, SessionStateType.CLOSED)
        assertStatus(bob, SessionStateType.CLOSED)

        assertLastSentSeqNum(alice, 3)
        assertLastReceivedSeqNum(bob, 3)
        assertLastSentSeqNum(bob, 1)
        assertLastReceivedSeqNum(alice, 1)
    }
}
