package net.corda.session.manager.integration.helper

import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.SessionParty
import net.corda.session.manager.integration.SessionPartyFactory

fun initiateNewSession(): Pair<SessionParty, SessionParty> {
    val (initiator, initiated) = SessionPartyFactory().createSessionParties()

    //send init
    initiator.processNewOutgoingMessage(SessionMessageType.INIT, sendMessages = true)
    assertStatus(initiator, SessionStateType.CREATED)

    //receive init and send ack
    initiated.processNextReceivedMessage(sendMessages = true)
    assertStatus(initiated, SessionStateType.CONFIRMED)

    //process ack
    initiator.processNextReceivedMessage()
    assertStatus(initiator, SessionStateType.CONFIRMED)

    return Pair(initiator, initiated)
}

fun closeSession(
    partyA: SessionParty,
    partyB: SessionParty
) {
    //partyA send close
    partyA.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
    assertStatus(partyA, SessionStateType.CLOSING)

    //partyB receive Close and send ack to partyA
    partyB.processNextReceivedMessage(sendMessages = true)
    assertStatus(partyB, SessionStateType.CLOSING)
    //partyA process ack
    partyA.processNextReceivedMessage()
    assertStatus(partyA, SessionStateType.CLOSING)


    //partyB send close to partyA
    partyB.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
    assertStatus(partyB, SessionStateType.WAIT_FOR_FINAL_ACK)
    //partyA receive close and send ack to partyB
    partyA.processNextReceivedMessage(sendMessages = true)
    assertStatus(partyA, SessionStateType.CLOSED)

    //partyB process ack
    partyB.processNextReceivedMessage()
    assertStatus(partyB, SessionStateType.CLOSED)
}

