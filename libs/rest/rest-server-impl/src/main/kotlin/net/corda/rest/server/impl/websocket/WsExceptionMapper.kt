package net.corda.rest.server.impl.websocket

import net.corda.rest.ws.WebSocketProtocolViolationException
import net.corda.rest.ws.WebSocketValidationException
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.StatusCode
import java.lang.IllegalArgumentException

// Maximum length for a closeStatus is 123 characters, otherwise a 1011 Server Error - Phrase exceeds maximum length of 123
fun Exception.mapToWsStatusCode(): CloseStatus {
    return when (val e = this) {
        is WebSocketValidationException -> {
            CloseStatus(StatusCode.BAD_DATA, "${e.message} - ${e.cause?.message}".take(123))
        }
        is WebSocketProtocolViolationException -> {
            CloseStatus(StatusCode.POLICY_VIOLATION, message?.take(123) ?: "Policy violation")
        }
        is IllegalArgumentException -> {
            CloseStatus(StatusCode.BAD_DATA, message?.take(123) ?: "Invalid data")
        }
        else -> {
            CloseStatus(StatusCode.SERVER_ERROR, message?.take(123) ?: "Server error")
        }
    }
}
