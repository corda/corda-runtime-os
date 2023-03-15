package net.corda.applications.workers.smoketest.websocket.client

import net.corda.e2etest.utilities.CLUSTER_URI
import net.corda.e2etest.utilities.PASSWORD
import net.corda.e2etest.utilities.USERNAME
import net.corda.e2etest.utilities.getOrThrow
import net.corda.test.util.consistently
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration
import java.util.LinkedList

fun useWebsocketConnection(
    path: String,
    block: (wsHandler: InternalWebsocketHandler) -> Unit
) {
    val wsHandler = MessageQueueWebSocketHandler()
    val client = SmokeTestWebsocketClient()

    client.use {
        it.start()
        it.connect(path, wsHandler)
        consistently(
            duration = Duration.ofSeconds(5),
            waitBefore = Duration.ofMillis(500)
        ) {
            assertThat(wsHandler.isConnected)
                .withFailMessage("web-socket-client should be connected")
                .isTrue
        }

        block.invoke(wsHandler)
    }

    eventually {
        assertThat(wsHandler.isConnected)
            .withFailMessage("web-socket-client should be disconnected")
            .isFalse
    }
}

class SmokeTestWebsocketClient(
    private val username: String = USERNAME,
    private val password: String = PASSWORD,
    private val connectTimeout: Duration = Duration.ofSeconds(10),
) : AutoCloseable {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val baseWssPath = with(URI("wss", CLUSTER_URI.schemeSpecificPart, CLUSTER_URI.fragment)) { "${this}api/v1" }
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