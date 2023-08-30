package net.corda.session.manager.integration.helper

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionConfirm
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.session.manager.integration.SessionMessageType
import net.corda.test.flow.util.buildSessionEvent
import java.nio.ByteBuffer
import java.time.Instant

fun generateMessage(
    messageType: SessionMessageType,
    instant: Instant,
    messageDirection: MessageDirection = MessageDirection.OUTBOUND
):
        SessionEvent {
    return when (messageType) {
        SessionMessageType.INIT -> generateInit(instant, messageDirection)
        SessionMessageType.CONFIRM -> generateConfirm(instant, messageDirection)
        SessionMessageType.DATA -> generateData(instant, messageDirection)
        SessionMessageType.ERROR -> generateError(instant, messageDirection)
        SessionMessageType.CLOSE -> generateClose(instant, messageDirection)
    }
}

fun generateInit(instant: Instant, messageDirection: MessageDirection = MessageDirection.OUTBOUND): SessionEvent {
    val sessionInit = SessionInit.newBuilder()
        .setCpiId("cpiId")
        .setFlowId(null)
        .setContextPlatformProperties(emptyKeyValuePairList())
        .setContextUserProperties(emptyKeyValuePairList())
        .build()
    return generateSessionEvent(sessionInit, instant, messageDirection)
}

fun generateData(instant: Instant, messageDirection: MessageDirection): SessionEvent {
    return generateSessionEvent(SessionData(ByteBuffer.wrap("bytes".toByteArray()), null), instant, messageDirection)
}

fun generateConfirm(instant: Instant, messageDirection: MessageDirection): SessionEvent {
    return generateSessionEvent(SessionConfirm(), instant, messageDirection)
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
    return buildSessionEvent(messageDirection, "sessionId", null, payload, instant, contextSessionProps = emptyKeyValuePairList())
}
