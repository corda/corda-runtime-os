package net.corda.applications.workers.smoketest.websocket.client

import java.util.Queue
import net.corda.applications.workers.smoketest.contextLogger
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.client.NoOpEndpoint

class MessageQueueWebsocketHandler/*<T : Any>*/(
    private val messageQueue: Queue<String>,
//    private val messageType: Class<T>
): NoOpEndpoint() {

    private companion object {
        val log = contextLogger()
    }

    override fun onWebSocketConnect(session: Session) {
        log.info("onWebSocketConnect : $session")
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        log.info("Reacting to server closed: $statusCode - $reason")
        super.onWebSocketClose(statusCode, reason)
    }

    override fun onWebSocketText(message: String) {
        log.info("Received message: $message")
//        messageQueue.add(jacksonObjectMapper().readValue(message, messageType))
        messageQueue.add(message)
    }
}
