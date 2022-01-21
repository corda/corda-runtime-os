package net.corda.session.manager.impl.processor.helper

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionError
import java.time.Instant

fun generateAckEvent(sequenceNum: Int, sessionId: String, instant: Instant): SessionEvent {
    return SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionId, null, SessionAck(sequenceNum))
}

fun generateErrorEvent(sessionId: String, errorMessage: String, errorType: String, instant: Instant): SessionEvent {
    val errorEnvelope = ExceptionEnvelope(errorType, errorMessage)
    val sessionError = SessionError(errorEnvelope)
    return SessionEvent(MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionId, null, sessionError)
}
