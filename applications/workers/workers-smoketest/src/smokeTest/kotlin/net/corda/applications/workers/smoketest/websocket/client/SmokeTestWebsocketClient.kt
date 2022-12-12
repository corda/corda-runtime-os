package net.corda.applications.workers.smoketest.websocket.client

import net.corda.applications.workers.smoketest.PASSWORD
import net.corda.applications.workers.smoketest.USERNAME
import java.net.URI
import java.time.Duration
import java.util.LinkedList
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
    block: (wsHandler: InternalWebsocketHandler) -> Unit
) {
    val wsHandler = MessageQueueWebSocketHandler()
    val client = SmokeTestWebsocketClient()

    client.use {
        it.start()
        it.connect(path, wsHandler)
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
    private val username: String = USERNAME,
    private val password: String = PASSWORD,
    private val connectTimeout: Duration = Duration.ofSeconds(10),
) : AutoCloseable {

    private companion object {
        val log = contextLogger()
        const val baseWssPath = "wss://localhost:8888/api/v1"
    }

    private val httpClient = HttpClient(SslContextFactory.Client(true))
    private val wsClient = WebSocketClient(httpClient)

    private val sessions: MutableList<Session> = LinkedList()

    fun start() {
        wsClient.start()
    }

    fun connect(path: String, webSocketAdapter: WebSocketAdapter): Session {
        val fullPath = "$baseWssPath$path"
        val sessionFuture = wsClient.connect(
            webSocketAdapter,
            URI(fullPath),
            ClientUpgradeRequest(),
            BasicAuthUpgradeListener(username, password)
        )
        val session = (sessionFuture.getOrThrow(connectTimeout)
            ?: throw SmokeTestWebsocketException("Session was null after ${connectTimeout.seconds} seconds."))

        sessions.add(session)

        log.info("Session established for $username at $fullPath.")
        log.info("Open sessions for this client: ${wsClient.openSessions.size}.")
        return session
    }

    override fun close() {
        log.info("Gracefully closing sessions.")
        sessions.forEach { it.close(CloseStatus(StatusCode.NORMAL, "Smoke test closing from client side")) }
        log.info("Gracefully closing WebSocket client.")
        wsClient.stop()
        log.info("Gracefully closing HTTP client.")
        httpClient.stop()
    }
}