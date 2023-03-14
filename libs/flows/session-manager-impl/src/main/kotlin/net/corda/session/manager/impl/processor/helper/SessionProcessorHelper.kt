package net.corda.session.manager.impl.processor.helper

import java.time.Instant
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity

/**
 * Generate an error SessionEvent.
 * @param sessionState Session state to build error for
 * @param sessionEvent Session event to build error for
 * @param errorMessage Error text
 * @param errorType Error type
 * @param instant timestamp for SessionEvent
 * @return SessionEvent with SessionError payload.
 */
fun generateErrorEvent(
    sessionState: SessionState,
    sessionEvent: SessionEvent,
    errorMessage: String,
    errorType: String,
    instant: Instant
): SessionEvent {
    return generateErrorEvent(
        sessionState,
        sessionEvent.initiatingIdentity,
        sessionEvent.initiatedIdentity,
        errorMessage,
        errorType,
        instant
    )
}

/**
 * Generate an error SessionEvent.
 * @param sessionState Session state to build error for
 * @param initiatingIdentity initiating identity
 * @param initiatedIdentity initiated identity
 * @param errorMessage The error message to provide along with this session error
 * @param errorType Error type
 * @param instant timestamp for SessionEvent
 * @return SessionEvent with SessionError payload.
 */
@Suppress("LongParameterList")
fun generateErrorEvent(
    sessionState: SessionState,
    initiatingIdentity: HoldingIdentity,
    initiatedIdentity: HoldingIdentity,
    errorMessage: String,
    errorType: String,
    instant: Instant,
): SessionEvent {
    val sessionId = sessionState.sessionId
    val errorEnvelope = ExceptionEnvelope(errorType, errorMessage)
    val sessionError = SessionError(errorEnvelope)
    return SessionEvent.newBuilder()
        .setMessageDirection(MessageDirection.OUTBOUND)
        .setTimestamp(instant)
        .setSequenceNum(null)
        .setInitiatingIdentity(initiatingIdentity)
        .setInitiatedIdentity(initiatedIdentity)
        .setSessionId(sessionId)
        .setReceivedSequenceNum(0)
        .setOutOfOrderSequenceNums(emptyList())
        .setPayload(sessionError)
        .build()
}

/**
 * Generate a new SessionState with a status of ERROR and an Error SessionEvent queued to send.
 * @param errorMessage Error text
 * @param sessionEvent SessionId to build a state for
 * @param errorType Error type
 * @param instant timestamp for SessionEvent
 * @return A new SessionState with a status of ERROR and an Error SessionEvent queued to send.
 */
fun generateErrorSessionStateFromSessionEvent(errorMessage: String, sessionEvent: SessionEvent, errorType: String, instant: Instant):
        SessionState {
    val sessionState = SessionState.newBuilder()
        .setSessionId(sessionEvent.sessionId)
        .setSessionStartTime(instant)
        .setLastReceivedMessageTime(instant)
        .setLastSentMessageTime(instant)
        .setCounterpartyIdentity(sessionEvent.initiatingIdentity)
        .setSendAck(false)
        .setReceivedEventsState(SessionProcessState(0, listOf()))
        .setSendEventsState(SessionProcessState(0, listOf()))
        .setStatus(SessionStateType.ERROR)
        .setHasScheduledCleanup(false)
        .setCounterpartySessionProperties(null)
        .build()

    val errorEvent = generateErrorEvent(sessionState, sessionEvent, errorMessage, errorType, instant)
    return sessionState.apply {
        sendEventsState.undeliveredMessages = sendEventsState.undeliveredMessages.plus(errorEvent)
    }
}

/**
 * Update and return the received events session state. Set the last processed sequence number to the last
 * contiguous event in the sequence of [undeliveredMessages].
 */
fun recalcReceivedProcessState(receivedEventsState: SessionProcessState): SessionProcessState {
    var nextSeqNum = receivedEventsState.lastProcessedSequenceNum + 1
    val undeliveredMessages = receivedEventsState.undeliveredMessages

    val sortedEvents = undeliveredMessages.distinctBy { it.sequenceNum }.sortedBy { it.sequenceNum }
    for (undeliveredMessage in sortedEvents) {
        if (undeliveredMessage.sequenceNum == nextSeqNum) {
            nextSeqNum++
        } else if (undeliveredMessage.sequenceNum < nextSeqNum) {
            continue
        } else {
            break
        }
    }

    return SessionProcessState(nextSeqNum - 1, sortedEvents)
}

/**
 * Convert a session state to an error state which a queued error message
 * @param sessionState input session state
 * @param sessionEvent input session event to get indentity info from
 * @param instant to generate timestamps for
 * @param errorMessage error message
 * @param errorType error type
 * @return session state updated to error state
 */
fun setErrorState(
    sessionState: SessionState,
    sessionEvent: SessionEvent,
    instant: Instant,
    errorMessage: String,
    errorType: String,
): SessionState {
    return sessionState.apply {
        status = SessionStateType.ERROR
        sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(
            generateErrorEvent(sessionState, sessionEvent, errorMessage, errorType, instant)
        )
    }
}