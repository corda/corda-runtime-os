package net.corda.session.manager.impl.processor.helper

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import java.time.Instant

//TODO - probably replace these generate methods with a reusable lib/api that the flow engine could use to generate SessionEvents to send
fun generateAckEvent(sequenceNum: Int, sessionId: String, instant: Instant): SessionEvent {
    return SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionId, null, SessionAck(sequenceNum))
}

fun generateErrorEvent(sessionId: String, errorMessage: String, errorType: String, instant: Instant): SessionEvent {
    val errorEnvelope = ExceptionEnvelope(errorType, errorMessage)
    val sessionError = SessionError(errorEnvelope)
    return SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionId, null, sessionError)
}

//TODO - re-examine how we will handle generation of a new error state
fun generateErrorSessionStateFromSessionEvent(sessionId: String, errorMessage: String, errorType: String, instant: Instant): SessionState {
    val errorEvent = generateErrorEvent(sessionId, errorMessage, errorType, instant)
    val errorSessionState = SessionState()
    errorSessionState.sessionId = sessionId
    errorSessionState.sessionStartTime = instant.toEpochMilli()
    errorSessionState.sendEventsState = SessionProcessState(0, mutableListOf(errorEvent))
    errorSessionState.status = SessionStateType.ERROR
    return errorSessionState
}
