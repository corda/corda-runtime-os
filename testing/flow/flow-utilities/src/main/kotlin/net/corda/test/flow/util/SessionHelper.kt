package net.corda.test.flow.util

import java.time.Instant
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity

@Suppress("LongParameterList")
fun buildSessionState(
    status: SessionStateType,
    lastReceivedSeqNum: Int,
    receivedEvents: List<SessionEvent>,
    lastSentSeqNum: Int,
    eventsToSend: List<SessionEvent>,
    sessionStartTime: Instant = Instant.now(),
    sessionId: String = "sessionId",
    counterpartyIdentity: HoldingIdentity = HoldingIdentity("Alice", "group1")
): SessionState {
    return SessionState.newBuilder()
        .setSessionId(sessionId)
        .setSessionStartTime(sessionStartTime)
        .setLastReceivedMessageTime(sessionStartTime)
        .setCounterpartyIdentity(counterpartyIdentity)
        .setReceivedEventsState(SessionProcessState(lastReceivedSeqNum, receivedEvents))
        .setSendEventsState(SessionProcessState(lastSentSeqNum, eventsToSend))
        .setStatus(status)
        .setHasScheduledCleanup(false)
        .setCounterpartySessionProperties(KeyValuePairList())
        .build()
}

@Suppress("LongParameterList")
fun buildSessionEvent(
    messageDirection: MessageDirection,
    sessionId: String,
    sequenceNum: Int?,
    payload: Any? = null,
    timestamp: Instant = Instant.now(),
    initiatingIdentity: HoldingIdentity = HoldingIdentity("alice", "group1"),
    initiatedIdentity: HoldingIdentity = HoldingIdentity("bob", "group1"),
): SessionEvent {
    return SessionEvent.newBuilder()
        .setSessionId(sessionId)
        .setMessageDirection(messageDirection)
        .setSequenceNum(sequenceNum)
        .setInitiatingIdentity(initiatingIdentity)
        .setInitiatedIdentity(initiatedIdentity)
        .setPayload(payload)
        .setTimestamp(timestamp)
        .build()
}
