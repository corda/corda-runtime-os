package net.corda.session.manager.integration.helper

import net.corda.data.flow.state.session.SessionStateType
import net.corda.libs.configuration.SmartConfig
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.SessionParty
import net.corda.session.manager.integration.SessionPartyFactory

fun initiateNewSession(config: SmartConfig): Pair<SessionParty, SessionParty> {
    val (initiator, initiated) = SessionPartyFactory().createSessionParties(config)

    //send init
    initiator.processNewOutgoingMessage(SessionMessageType.INIT, sendMessages = true)
    initiator.assertStatus(SessionStateType.CREATED)

    initiated.processNextReceivedMessage(sendMessages = true)
    initiated.assertStatus(SessionStateType.CONFIRMED)

    //process ack
    initiated.processNewOutgoingMessage(SessionMessageType.CONFIRM, sendMessages = true)
    initiator.processNextReceivedMessage()
    initiator.assertStatus(SessionStateType.CONFIRMED)
    initiated.assertStatus(SessionStateType.CONFIRMED)

    initiated.assertAllMessagesDelivered()
    initiator.assertAllMessagesDelivered()

    return Pair(initiator, initiated)
}

fun closeSession(
    partyA: SessionParty,
    partyB: SessionParty
) {
    //partyB send close to partyA
    partyB.processNewOutgoingMessage(SessionMessageType.CLOSE, sendMessages = true)
    partyB.assertStatus(SessionStateType.CLOSED)

    //partyA receive close and send ack to partyB
    partyA.processNextReceivedMessage(sendMessages = true)
    partyA.assertStatus(SessionStateType.CLOSED)
}

