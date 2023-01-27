package net.corda.httprpc.server.impl.websocket

import java.lang.IllegalArgumentException
import net.corda.httprpc.ws.WebSocketProtocolViolationException
import net.corda.httprpc.ws.WebSocketValidationException
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.StatusCode

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
