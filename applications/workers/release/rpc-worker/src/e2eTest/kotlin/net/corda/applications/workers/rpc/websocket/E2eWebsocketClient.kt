package net.corda.applications.workers.rpc.websocket

import java.net.URI
import java.time.Duration
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient

class E2eWebsocketClient(
    private val wsHandler: WebSocketAdapter = NoOpWebsocketHandler(),
    private val username: String = "admin",
    private val password: String = "admin",
    private val connectTimeout: Duration = Duration.ofSeconds(10),
) : AutoCloseable {

    private companion object {
        val log = contextLogger()
        const val baseWssPath = "wss://localhost:8888/api/v1"
    }

    private val wsClient = WebSocketClient(HttpClient(SslContextFactory.Client(true)))

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
        val session = sessionFuture.getOrThrow(connectTimeout)
            ?: throw E2eWebsocketException("Session was null after ${connectTimeout.seconds} seconds.")

        log.info("Session established: $session")
        return session
    }

    override fun close() {
        wsClient.stop()
    }
}

class E2eWebsocketException(message: String) : Exception(message)