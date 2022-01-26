package net.corda.session.manager.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity

fun buildSessionState(
    status: SessionStateType,
    lastReceivedSeqNum: Int,
    receivedEvents: List<SessionEvent>,
    lastSentSeqNum: Int,
    eventsToSend: List<SessionEvent>
): SessionState {
    return SessionState.newBuilder()
        .setSessionId("sessionId")
        .setSessionStartTime(1)
        .setCounterpartyIdentity(HoldingIdentity("Alice", "group1"))
        .setIsInitiator(true)
        .setReceivedEventsState(SessionProcessState(lastReceivedSeqNum, receivedEvents))
        .setSentEventsState(SessionProcessState(lastSentSeqNum, eventsToSend))
        .setStatus(status)
        .build()
}
