package net.corda.test.flow.util

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
    sendAck: Boolean = false,
    sessionId: String = "sessionId"
): SessionState {
    return SessionState.newBuilder()
        .setSessionId(sessionId)
        .setSessionStartTime(sessionStartTime)
        .setLastReceivedMessageTime(sessionStartTime)
        .setLastSentMessageTime(sessionStartTime)
        .setCounterpartyIdentity(HoldingIdentity("Alice", "group1"))
        .setIsInitiator(true)
        .setSendAck(sendAck)
        .setReceivedEventsState(SessionProcessState(lastReceivedSeqNum, receivedEvents))
        .setSendEventsState(SessionProcessState(lastSentSeqNum, eventsToSend))
        .setStatus(status)
        .build()
}

@Suppress("LongParameterList")
fun buildSessionEvent(
    messageDirection: MessageDirection,
    sessionId: String,
    sequenceNum: Int?,
    payload: Any? = null,
    receivedSequenceNum: Int = 0,
    outOfOrderSeqNums: List<Int> = emptyList(),
    timestamp: Instant = Instant.now(),
): SessionEvent {
    return SessionEvent.newBuilder()
        .setSessionId(sessionId)
        .setMessageDirection(messageDirection)
        .setSequenceNum(sequenceNum)
        .setPayload(payload)
        .setTimestamp(timestamp)
        .setReceivedSequenceNum(receivedSequenceNum)
        .setOutOfOrderSequenceNums(outOfOrderSeqNums)
        .build()
}
