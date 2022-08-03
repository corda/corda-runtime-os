package net.corda.applications.workers.smoketest.websocket.client

import java.net.URI
import java.time.Duration
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import net.corda.applications.workers.smoketest.contextLogger
import net.corda.applications.workers.smoketest.getOrThrow
import net.corda.test.util.eventually
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

fun useWebsocketConnection(
    path: String,
    messageQueue: Queue<String> = ConcurrentLinkedQueue(),
    block: (wsHandler: InternalWebsocketHandler) -> Unit
) {
    val wsHandler = MessageQueueWebsocketHandler(messageQueue)
    val client = SmokeTestWebsocketClient(wsHandler)

    client.use {
        it.start()
        it.connect(path)
        eventually {
            assertTrue(wsHandler.isConnected)
        }
        block.invoke(wsHandler)
    }

    eventually {
        assertFalse(wsHandler.isConnected)
    }
}

class SmokeTestWebsocketClient(
    private val wsHandler: WebSocketAdapter,
    private val username: String = "admin",
    private val password: String = "admin",
    private val connectTimeout: Duration = Duration.ofSeconds(10),
) : AutoCloseable {

    private companion object {
        val log = contextLogger()
        const val baseWssPath = "wss://localhost:8888/api/v1"
    }

    private var wsClient = WebSocketClient(HttpClient(SslContextFactory.Client(true)))
    private var session: Session? = null

    fun start() {
        wsClient.start()
    }

    fun connect(path: String): Session {
        val sessionFuture = wsClient.connect(
            wsHandler,
            URI("$baseWssPath$path"),
            ClientUpgradeRequest(),
            BasicAuthUpgradeListener(username, password)
        )
        session = sessionFuture.getOrThrow(connectTimeout)
            ?: throw SmokeTestWebsocketException("Session was null after ${connectTimeout.seconds} seconds.")

        log.info("Session established: $session")
        return session!!
    }

    override fun close() {
        log.info("Gracefully closing session.")
        wsClient.stop()
        session?.close(CloseStatus(StatusCode.NORMAL, "Gracefully closing session from client side."))
        session = null
    }
}

class SmokeTestWebsocketException(message: String) : Exception(message)