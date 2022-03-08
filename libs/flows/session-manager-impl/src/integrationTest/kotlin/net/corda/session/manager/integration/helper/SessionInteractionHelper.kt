package net.corda.session.manager.integration.helper

import net.corda.data.flow.state.session.SessionStateType
import net.corda.libs.configuration.SmartConfig
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.SessionParty
import net.corda.session.manager.integration.SessionPartyFactory

fun initiateNewSession(config: SmartConfig): Pair<SessionParty, SessionParty> {
    val (initiator, initiated) = SessionPartyFactory().createSessionParties(config)

    initiator.assertStatus(null)
    initiated.assertStatus(null)

    //send init
    initiator.processNewOutgoingMessage(SessionMessageType.INIT, sendMessages = true)
    initiator.assertStatus(SessionStateType.CREATED)
    initiated.assertStatus(null)

    //receive init and send ack
    initiated.processNextReceivedMessage(sendMessages = true)
    initiated.assertStatus(SessionStateType.CONFIRMED)
    initiator.assertStatus(SessionStateType.CREATED)

    //process ack
    initiator.processNextReceivedMessage()
    initiator.assertStatus(SessionStateType.CONFIRMED)
    initiated.assertStatus(SessionStateType.CONFIRMED)

    initiator.assertIsInitiator(true)
    initiated.assertIsInitiator(false)

    initiated.assertAllMessagesDelivered()
    initiator.assertAllMessagesDelivered()

    return Pair(initiator, initiated)
}

fun closeSession(
    partyA: SessionParty,
    partyB: SessionParty
) {
    //partyA send close
    partyA.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
    partyA.assertStatus(SessionStateType.CLOSING)

    //partyB receive Close and send ack to partyA
    partyB.processNextReceivedMessage(sendMessages = true)
    partyB.assertStatus(SessionStateType.CLOSING)
    //partyA process ack
    partyA.processNextReceivedMessage()
    partyA.assertStatus(SessionStateType.CLOSING)


    //partyB send close to partyA
    partyB.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
    partyB.assertStatus(SessionStateType.WAIT_FOR_FINAL_ACK)
    //partyA receive close and send ack to partyB
    partyA.processNextReceivedMessage(sendMessages = true)
    partyA.assertStatus(SessionStateType.CLOSED)

    //partyB process ack
    partyB.processNextReceivedMessage()
    partyB.assertStatus(SessionStateType.CLOSED)
}

