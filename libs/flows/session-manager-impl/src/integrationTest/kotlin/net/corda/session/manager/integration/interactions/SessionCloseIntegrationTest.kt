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

class SessionCloseIntegrationTest {

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
    fun `Full session send and receive with standard close`() {
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
    fun `Full session send and receive with close, alices close is dropped`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //alice send data
        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        //bob receive data and send ack
        bob.processNextReceivedMessage(sendMessages = true)
        //process ack
        alice.processNextReceivedMessage()

        alice.assertAllMessagesDelivered()
        bob.assertAllMessagesDelivered()

        //alice send close
        alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        alice.assertStatus(SessionStateType.CLOSING)

        //alice close is dropped
        bob.dropNextInboundMessage()
        //nothing to process
        bob.processNextReceivedMessage(sendMessages = true)
        bob.assertStatus(SessionStateType.CONFIRMED)

        //alice receives nothing
        assertThat(alice.getInboundMessageSize()).isEqualTo(0)
        alice.assertStatus(SessionStateType.CLOSING)

        //bob send close to alice
        bob.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        bob.assertStatus(SessionStateType.CLOSING)
        //alice receive close and send ack to bob
        alice.processNextReceivedMessage(sendMessages = true)
        alice.assertStatus(SessionStateType.WAIT_FOR_FINAL_ACK)

        //bob process ack
        bob.processNextReceivedMessage()
        bob.assertStatus(SessionStateType.CLOSING)

        alice.assertLastSentSeqNum(3)
        bob.assertLastReceivedSeqNum(2)
        bob.assertLastSentSeqNum(1)
        alice.assertLastReceivedSeqNum(1)
    }

    @Test
    fun `Simultaneous close`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //bob and alice send close
        bob.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        alice.assertStatus(SessionStateType.CLOSING)
        bob.assertStatus(SessionStateType.CLOSING)

        //bob receive Close and send ack back as well as resend close
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
    fun `Simultaneous close, ack for bobs close is dropped`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //bob and alice send close
        bob.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        alice.assertStatus(SessionStateType.CLOSING)
        bob.assertStatus(SessionStateType.CLOSING)

        //bob receive Close and send ack back as well
        bob.processNextReceivedMessage(sendMessages = true)
        //alice receive close and send ack back
        alice.processNextReceivedMessage(sendMessages = true)
        bob.assertStatus(SessionStateType.WAIT_FOR_FINAL_ACK)
        alice.assertStatus(SessionStateType.WAIT_FOR_FINAL_ACK)

        //drop ack for bobs close
        bob.dropNextInboundMessage()

        //alice and bob process duplicate closes as well as acks
        alice.processAllReceivedMessages(sendMessages = true)
        alice.assertStatus(SessionStateType.CLOSED)
        bob.processAllReceivedMessages(sendMessages = true)
        bob.assertStatus(SessionStateType.WAIT_FOR_FINAL_ACK)
        alice.processAllReceivedMessages()

        alice.assertLastSentSeqNum(2)
        bob.assertLastReceivedSeqNum(2)
        bob.assertLastSentSeqNum(1)
        alice.assertLastReceivedSeqNum(1)
    }

    @Test
    fun `Send error while both parties waiting for final ack`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        //bob and alice send close
        bob.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        alice.assertStatus(SessionStateType.CLOSING)
        bob.assertStatus(SessionStateType.CLOSING)

        //bob receive Close, does not send ack
        bob.processNextReceivedMessage()
        //alice receive close and send ack back, also resend close to bob as ack not yet received
        alice.processNextReceivedMessage(sendMessages = true)
        bob.assertStatus(SessionStateType.WAIT_FOR_FINAL_ACK)
        alice.assertStatus(SessionStateType.WAIT_FOR_FINAL_ACK)

        //alice send error
        alice.processNewOutgoingMessage(SessionMessageType.ERROR, sendMessages = true)

        //alice receives final ack and duplicate close from bob
        bob.sendMessages()
        alice.processAllReceivedMessages(sendMessages = true)
        //error state remains unchanged
        alice.assertStatus(SessionStateType.ERROR)

        bob.processNextReceivedMessage(sendMessages = true)
        bob.assertStatus(SessionStateType.CLOSED)

        bob.processAllReceivedMessages(sendMessages = true)
        bob.assertStatus(SessionStateType.ERROR)

        //close cannot be retrieved as status never went to closed
        assertThat(alice.sessionState?.receivedEventsState?.undeliveredMessages?.size).isEqualTo(1)
        //close for bob was passed to client as error was received after that was processed
        bob.assertAllMessagesDelivered()

        alice.assertLastSentSeqNum(2)
        alice.assertLastReceivedSeqNum(1)
        bob.assertLastReceivedSeqNum(2)
        bob.assertLastSentSeqNum(1)
    }

    @Test
    fun `Out Of order close message interlooped with late data message and additional duplicate Close`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)

        alice.processNewOutgoingMessage(SessionMessageType.DATA)
        alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        //bob loses data messages
        bob.dropInboundMessage(0)
        alice.sendMessages(Instant.now().plusMillis(FIVE_SECONDS)) //Bob inbound queue is now CLOSE, DATA, CLOSE

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

    @Test
    fun `Normal session is closed successfully, then additional new close is attempted`() {
        val (alice, bob) = initiateNewSession(testSmartConfig)
        closeSession(alice, bob)

        alice.assertStatus(SessionStateType.CLOSED)
        bob.assertStatus(SessionStateType.CLOSED)

        //new close message with next seq num (can only happen from bug), triggers error
        alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        bob.processNextReceivedMessage(sendMessages = true)
        alice.processNextReceivedMessage()

        alice.assertStatus(SessionStateType.CLOSED)
        bob.assertStatus(SessionStateType.CLOSED)
    }
}
