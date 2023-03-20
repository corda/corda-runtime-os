package net.corda.session.manager.integration.helper

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.session.manager.integration.SessionMessageType
import net.corda.test.flow.util.buildSessionEvent

fun generateMessage(
    messageType: SessionMessageType,
    instant: Instant,
    messageDirection: MessageDirection = MessageDirection.OUTBOUND
):
        SessionEvent {
    return when (messageType) {
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
        .setFlowId(null)
        .setPayload(ByteBuffer.wrap("some bytes".toByteArray()))
        .setContextPlatformProperties(emptyKeyValuePairList())
        .setContextUserProperties(emptyKeyValuePairList())
        .setContextSessionProperties(emptyKeyValuePairList())
        .build()
    return generateSessionEvent(sessionInit, instant, messageDirection)
}

fun generateData(instant: Instant, messageDirection: MessageDirection): SessionEvent {
    return generateSessionEvent(SessionData(ByteBuffer.wrap("bytes".toByteArray())), instant, messageDirection)
}

fun generateAck(instant: Instant, messageDirection: MessageDirection = MessageDirection.OUTBOUND): SessionEvent {
    return generateSessionEvent(SessionAck(), instant, messageDirection)
}

fun generateError(instant: Instant, messageDirection: MessageDirection): SessionEvent {
    return generateSessionEvent(
        SessionError(ExceptionEnvelope("error type", "error message")),
        instant,
        messageDirection
    )
}

fun generateClose(instant: Instant, messageDirection: MessageDirection): SessionEvent {
    return generateSessionEvent(SessionClose(), instant, messageDirection)
}

fun generateSessionEvent(payload: Any, instant: Instant, messageDirection: MessageDirection): SessionEvent {
    return buildSessionEvent(messageDirection, "sessionId", null, payload, 0, mutableListOf(), instant)
}
