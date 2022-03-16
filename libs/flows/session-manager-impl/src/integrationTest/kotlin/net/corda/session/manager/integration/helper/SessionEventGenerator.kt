package net.corda.session.manager.integration.helper

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.test.flow.util.buildSessionEvent
import net.corda.session.manager.integration.SessionMessageType
import java.nio.ByteBuffer
import java.time.Instant

fun generateMessage(messageType: SessionMessageType, instant: Instant, messageDirection: MessageDirection = MessageDirection.OUTBOUND) :
        SessionEvent {
    return when(messageType) {
        SessionMessageType.INIT -> generateInit(instant, messageDirection)
        SessionMessageType.DATA -> generateData(instant, messageDirection)
        SessionMessageType.ERROR -> generateError(instant, messageDirection)
        SessionMessageType.CLOSE -> generateClose(instant, messageDirection)
        SessionMessageType.ACK -> generateAck(instant, messageDirection)
    }
}

fun generateInit(instant: Instant, messageDirection: MessageDirection = MessageDirection.OUTBOUND): SessionEvent {
    val sessionInit = SessionInit.newBuilder()
        .setCpiId("cpiId")
        .setFlowKey(null)
        .setFlowName("someflow")
        .setPayload(ByteBuffer.wrap("some bytes".toByteArray()))
        .setInitiatingIdentity(HoldingIdentity("Alice","group1" ))
        .setInitiatedIdentity(HoldingIdentity("Bob","group1" ))
        .build()
    return generateSessionEvent(sessionInit, instant, messageDirection)
}

fun generateData(instant: Instant, messageDirection: MessageDirection): SessionEvent {
    return generateSessionEvent(SessionData(), instant, messageDirection)
}

fun generateAck(instant: Instant, messageDirection: MessageDirection = MessageDirection.OUTBOUND): SessionEvent {
    return generateSessionEvent(SessionAck(), instant, messageDirection)
}

fun generateError(instant: Instant, messageDirection: MessageDirection): SessionEvent {
    return generateSessionEvent(SessionError(ExceptionEnvelope("error type", "error message")), instant, messageDirection)
}

fun generateClose(instant: Instant, messageDirection: MessageDirection): SessionEvent {
    return generateSessionEvent(SessionClose(), instant, messageDirection)
}

fun generateSessionEvent(payload: Any, instant: Instant, messageDirection: MessageDirection): SessionEvent {
    return buildSessionEvent(messageDirection, "sessionId", null, payload, 0, mutableListOf(), instant)
}
