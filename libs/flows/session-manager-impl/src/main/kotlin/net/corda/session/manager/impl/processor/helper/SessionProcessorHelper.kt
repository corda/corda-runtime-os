package net.corda.session.manager.impl.processor.helper

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import java.time.Instant

fun generateAckEvent(sequenceNum: Int, sessionId: String, instant: Instant): SessionEvent {
    return SessionEvent(MessageDirection.OUTBOUND, instant, sessionId, null, SessionAck(sequenceNum))
}

fun generateErrorEvent(sessionId: String, errorMessage: String, errorType: String, instant: Instant): SessionEvent {
    val errorEnvelope = ExceptionEnvelope(errorType, errorMessage)
    val sessionError = SessionError(errorEnvelope)
    return SessionEvent(MessageDirection.OUTBOUND, instant, sessionId, null, sessionError)
}

fun generateErrorSessionStateFromSessionEvent(sessionId: String, errorMessage: String, errorType: String, instant: Instant): SessionState {
    val errorEvent = generateErrorEvent(sessionId, errorMessage, errorType, instant)
    val counterparty = HoldingIdentity("Unknown-x500Name","Unknown-groupId")
    return SessionState.newBuilder()
        .setSessionId(sessionId)
        .setSessionStartTime(instant)
        .setLastReceivedMessageTime(instant)
        .setLastSentMessageTime(instant)
        .setIsInitiator(false)
        .setCounterpartyIdentity(counterparty)
        .setReceivedEventsState(SessionProcessState(0, listOf()))
        .setSendEventsState(SessionProcessState(0, listOf(errorEvent)))
        .setStatus(SessionStateType.ERROR)
        .build()
}

/**
 * Update and return the received events session state. Set the last processed sequence number to the last
 * contiguous event in the sequence of [undeliveredMessages].
 */
fun recalcReceivedProcessState(receivedEventsState: SessionProcessState) : SessionProcessState {
    var nextSeqNum = receivedEventsState.lastProcessedSequenceNum+1
    val undeliveredMessages = receivedEventsState.undeliveredMessages

    val sortedEvents = undeliveredMessages.distinctBy { it.sequenceNum }.sortedBy { it.sequenceNum }
    for (undeliveredMessage in sortedEvents) {
        if (undeliveredMessage.sequenceNum == nextSeqNum) {
            nextSeqNum++
        } else {
            break
        }
    }

    return SessionProcessState(nextSeqNum - 1, sortedEvents)
}
