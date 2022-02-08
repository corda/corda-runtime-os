package net.corda.session.manager.integration.tests

import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.helper.assertAllMessagesDelivered
import net.corda.session.manager.integration.helper.assertLastReceivedSeqNum
import net.corda.session.manager.integration.helper.assertLastSentSeqNum
import net.corda.session.manager.integration.helper.assertStatus
import net.corda.session.manager.integration.helper.closeSession
import net.corda.session.manager.integration.helper.initiateNewSession
import org.junit.jupiter.api.Test

class SessionManagerIntegrationTest {

    @Test
    fun `Full Session Send And Receive`() {
        val (alice, bob) = initiateNewSession()

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
    fun `Simultaneous Close`() {
        val (alice, bob) = initiateNewSession()

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
        val (alice, bob) = initiateNewSession()

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
        val (alice, bob) = initiateNewSession()

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
        val (alice, bob) = initiateNewSession()

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
            processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
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


        alice.assertLastSentSeqNum(4)
        bob.assertLastReceivedSeqNum(4)
        bob.assertLastSentSeqNum(1)
        alice.assertLastReceivedSeqNum(1)
    }

    @Test
    fun `Out Of Order Close Message With Duplicate Close`() {
        val (alice, bob) = initiateNewSession()

        alice.processNewOutgoingMessage(SessionMessageType.DATA)
        alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
        //bob loses data messages
        bob.dropInboundMessage(0)
        alice.sendMessages() //Bob inbound queue is now CLOSE, DATA, CLOSE

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
