package net.corda.applications.workers.smoketest.websocket.client

import java.io.IOException
import java.util.Queue
import net.corda.applications.workers.smoketest.contextLogger
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.client.NoOpEndpoint

class MessageQueueWebsocketHandler(
    override val messageQueue: Queue<String>,
) : NoOpEndpoint(), InternalWebsocketHandler {

    private companion object {
        val log = contextLogger()
    }

    override fun onWebSocketConnect(session: Session) {
        log.info("onWebSocketConnect : $session")
        super.onWebSocketConnect(session)
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        log.info("Reacting to server closed: $statusCode - $reason")
        super.onWebSocketClose(statusCode, reason)
    }

    override fun onWebSocketText(message: String) {
        log.info("Received message: $message")
        messageQueue.add(message)
    }

    override fun send(message: String) {
        if(super.isConnected()) {
            try {
                log.info("Attempting to send message from client websocket handler to server. Message: $message")
                remote.sendString(message)
            } catch (e: IOException) {
                log.warn("Exception sending message to server.", e)
            }
        } else {
            log.warn("Attempted to send message from client to server but session was not connected.")
        }
    }
}
