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

fun generateInit(): SessionEvent {
    val sessionInit = SessionInit.newBuilder()
        .setCpiId("cpiId")
        .setFlowKey(null)
        .setFlowName("someflow")
        .setPayload(ByteBuffer.wrap("some bytes".toByteArray()))
        .setInitiatingIdentity(HoldingIdentity("Alice","group1" ))
        .setInitiatedIdentity(HoldingIdentity("Bob","group1" ))
        .build()
    return generateSessionEvent(sessionInit)
}

fun generateData(): SessionEvent {
    return generateSessionEvent(SessionData())
}

fun generateError(): SessionEvent {
    return generateSessionEvent(SessionError(ExceptionEnvelope("error type", "error message")))
}

fun generateClose(): SessionEvent {
    return generateSessionEvent(SessionClose())
}

fun generateSessionEvent(payload: Any): SessionEvent {
    return SessionEvent.newBuilder()
        .setSessionId("sessionId")
        .setSequenceNum(null)
        .setTimestamp(null)
        .setMessageDirection(MessageDirection.OUTBOUND)
        .setPayload(payload)
        .build()
}
