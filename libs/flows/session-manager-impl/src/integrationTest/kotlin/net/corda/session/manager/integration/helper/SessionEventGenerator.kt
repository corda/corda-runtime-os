package net.corda.session.manager.integration.helper

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import java.nio.ByteBuffer
import java.time.Instant

fun generateInit(instant: Instant): SessionEvent {
    val sessionInit = SessionInit.newBuilder()
        .setCpiId("cpiId")
        .setFlowKey(null)
        .setFlowName("someflow")
        .setPayload(ByteBuffer.wrap("some bytes".toByteArray()))
        .setInitiatingIdentity(HoldingIdentity("Alice","group1" ))
        .setInitiatedIdentity(HoldingIdentity("Bob","group1" ))
        .build()
    return generateSessionEvent(sessionInit, instant)
}

fun generateData(instant: Instant): SessionEvent {
    return generateSessionEvent(SessionData(), instant)
}

fun generateError(instant: Instant): SessionEvent {
    return generateSessionEvent(SessionError(ExceptionEnvelope("error type", "error message")), instant)
}

fun generateClose(instant: Instant): SessionEvent {
    return generateSessionEvent(SessionClose(), instant)
}

fun generateSessionEvent(payload: Any, instant: Instant): SessionEvent {
    return SessionEvent.newBuilder()
        .setSessionId("sessionId")
        .setSequenceNum(null)
        .setTimestamp(instant)
        .setMessageDirection(MessageDirection.OUTBOUND)
        .setPayload(payload)
        .build()
}
