package net.corda.test.flow.util

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import java.time.Instant

@Suppress("LongParameterList")
fun buildSessionState(
    status: SessionStateType,
    lastReceivedSeqNum: Int,
    receivedEvents: List<SessionEvent>,
    lastSentSeqNum: Int,
    eventsToSend: List<SessionEvent>,
    sessionStartTime: Instant = Instant.now(),
    sessionId: String = "sessionId",
    counterpartyIdentity: HoldingIdentity = HoldingIdentity("Alice", "group1"),
    sessionProperties: KeyValuePairList? = null
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
        .setSessionProperties(sessionProperties)
        .build()
}

@Suppress("LongParameterList")
fun buildSessionEvent(
    messageDirection: MessageDirection,
    sessionId: String,
    sequenceNum: Int?,
    payload: Any? = null,
    timestamp: Instant = Instant.now(),
    initiatingIdentity: HoldingIdentity = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group1"),
    initiatedIdentity: HoldingIdentity = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group1"),
    contextSessionProps: KeyValuePairList? = null
): SessionEvent {
    return SessionEvent.newBuilder()
        .setSessionId(sessionId)
        .setMessageDirection(messageDirection)
        .setSequenceNum(sequenceNum)
        .setInitiatingIdentity(initiatingIdentity)
        .setInitiatedIdentity(initiatedIdentity)
        .setPayload(payload)
        .setTimestamp(timestamp)
        .setContextSessionProperties(contextSessionProps)
        .build()
}
