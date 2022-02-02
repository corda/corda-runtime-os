package net.corda.session.manager.integration

import net.corda.data.flow.state.session.SessionStateType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionManagerIntegrationTest {


    //TODO more elaborate test cases. these are in place to illustrate the use of the api

    @Test
    fun testFullSessionSendAndReceive() {
        val (alice, bob) = initiateNewSession()

        //alice send data
        alice.processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        //bob receive data and send ack
        bob.processNextReceivedMessage(sendMessages = true)
        //process ack
        alice.processNextReceivedMessage()

        closeSession(alice, bob)

        assertThat(alice.sessionState?.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(bob.sessionState?.status).isEqualTo(SessionStateType.CLOSED)
    }

    @Test
    fun testOutOfOrderRandomShuffle() {
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

        closeSession(alice, bob)
        assertThat(alice.sessionState?.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(bob.sessionState?.status).isEqualTo(SessionStateType.CLOSED)
    }

    @Test
    fun testOutOfOrderReversedInbox() {
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

        closeSession(alice, bob)
        assertThat(alice.sessionState?.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(bob.sessionState?.status).isEqualTo(SessionStateType.CLOSED)
    }

    @Test
    fun testOutOfOrderDataWithDuplicateDataResends() {
        val (alice, bob) = initiateNewSession()

        //alice send 2 data
        alice.apply {
            processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
            processNewOutgoingMessage(SessionMessageType.DATA, sendMessages = true)
        }

        //bob receive data out of order and send 1 ack back
        bob.apply {
            processReceivedMessage(3, sendMessages = true)
            processReceivedMessage(2)
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

        assertThat(alice.sessionState?.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(bob.sessionState?.status).isEqualTo(SessionStateType.CLOSED)
    }
}


fun initiateNewSession(): Pair<SessionParty, SessionParty> {
    val (initiator, initiated) = SessionPartyFactory().createSessionParties()

    //send init
    initiator.processNewOutgoingMessage(SessionMessageType.INIT, sendMessages = true)
    //receive init and send ack
    initiated.processNextReceivedMessage(sendMessages = true)
    //process ack
    initiator.processNextReceivedMessage()

    return Pair(initiator, initiated)
}

fun closeSession(
    alice: SessionParty,
    bob: SessionParty
) {
    //alice send close
    alice.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
    //bob receive Close and send ack to alice
    bob.processNextReceivedMessage(sendMessages = true)
    //alice process ack
    alice.processNextReceivedMessage()


    //bob send close to alice
    bob.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
    //alice receive close and send ack to bob
    alice.processNextReceivedMessage(sendMessages = true)
    //bob process ack
    bob.processNextReceivedMessage()
}

