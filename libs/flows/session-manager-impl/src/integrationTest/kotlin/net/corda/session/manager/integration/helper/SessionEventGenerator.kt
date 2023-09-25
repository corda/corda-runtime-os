package net.corda.session.manager.integration.helper

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionCounterpartyInfoRequest
import net.corda.data.flow.event.session.SessionCounterpartyInfoResponse
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.flow.utils.keyValuePairListOf
import net.corda.session.manager.Constants
import net.corda.session.manager.integration.SessionMessageType
import net.corda.test.flow.util.buildSessionEvent
import java.nio.ByteBuffer
import java.time.Instant

fun generateMessage(
    messageType: SessionMessageType,
    instant: Instant,
    messageDirection: MessageDirection = MessageDirection.OUTBOUND,
    sessionId: String = "sessionId"
): SessionEvent {
    return when (messageType) {
        SessionMessageType.COUNTERPARTY_INFO -> generateCounterpartyInfoRQ(instant, messageDirection, sessionId)
        SessionMessageType.CONFIRM -> generateConfirm(instant, messageDirection, sessionId)
        SessionMessageType.DATA -> generateData(instant, messageDirection, sessionId)
        SessionMessageType.ERROR -> generateError(instant, messageDirection, sessionId)
        SessionMessageType.CLOSE -> generateClose(instant, messageDirection, sessionId)
    }
}

fun generateCounterpartyInfoRQ(instant: Instant, messageDirection: MessageDirection = MessageDirection.OUTBOUND, sessionId: String):
        SessionEvent {
    val sessionInit = SessionInit.newBuilder()
        .setCpiId("cpiId")
        .setFlowId(null)
        .setContextPlatformProperties(emptyKeyValuePairList())
        .setContextUserProperties(emptyKeyValuePairList())
        .build()
    return generateSessionEvent(SessionCounterpartyInfoRequest(sessionInit), instant, messageDirection, sessionId)
}

fun generateData(instant: Instant, messageDirection: MessageDirection, sessionId: String): SessionEvent {
    return generateSessionEvent(SessionData(ByteBuffer.wrap("bytes".toByteArray()), null), instant, messageDirection, sessionId)
}

fun generateConfirm(instant: Instant, messageDirection: MessageDirection, sessionId: String): SessionEvent {
    return generateSessionEvent(SessionCounterpartyInfoResponse(), instant, messageDirection, sessionId)
}

fun generateError(instant: Instant, messageDirection: MessageDirection, sessionId: String): SessionEvent {
    return generateSessionEvent(
        SessionError(ExceptionEnvelope("error type", "error message")),
        instant,
        messageDirection,
        sessionId
    )
}

fun generateClose(instant: Instant, messageDirection: MessageDirection, sessionId: String): SessionEvent {
    return generateSessionEvent(SessionClose(), instant, messageDirection, sessionId)
}

fun generateSessionEvent(payload: Any, instant: Instant, messageDirection: MessageDirection, sessionId: String): SessionEvent {
    return buildSessionEvent(messageDirection, sessionId, null, payload, instant,
        contextSessionProps = keyValuePairListOf(mapOf(Constants.FLOW_SESSION_REQUIRE_CLOSE to true.toString())))
}
