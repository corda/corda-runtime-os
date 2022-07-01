package net.corda.httprpc.server.impl

import io.javalin.core.util.Header.ACCESS_CONTROL_ALLOW_CREDENTIALS
import io.javalin.core.util.Header.ACCESS_CONTROL_ALLOW_ORIGIN
import io.javalin.core.util.Header.CACHE_CONTROL
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.WebRequest
import net.corda.httprpc.test.utils.findFreePort
import net.corda.httprpc.test.utils.multipartDir
import net.corda.httprpc.tools.HttpVerb.GET
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.NoOpEndpoint
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class HttpRpcServerWebsocketTest : HttpRpcServerTestBase() {
    private companion object {

        val log = contextLogger()

        private val httpRpcSettings = HttpRpcSettings(
            NetworkHostAndPort("localhost", findFreePort()),
            context,
            null,
            null,
            HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE
        )

        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            server = HttpRpcServerImpl(
                listOf(
                    TestHealthCheckAPIImpl()
                ),
                securityManager,
                httpRpcSettings,
                multipartDir,
                true
            ).apply { start() }
            client = TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.stop()
            }
        }
    }

    @AfterEach
    fun reset() {
        securityManager.forgetChecks()
    }

    @Test
    fun `valid path returns 200 OK`() {

        val getPathResponse = client.call(GET, WebRequest<Any>("health/sanity"), userName, password)
        assertEquals(HttpStatus.SC_OK, getPathResponse.responseStatus)
        assertEquals("localhost", getPathResponse.headers[ACCESS_CONTROL_ALLOW_ORIGIN])
        assertEquals("true", getPathResponse.headers[ACCESS_CONTROL_ALLOW_CREDENTIALS])
        assertEquals("no-cache", getPathResponse.headers[CACHE_CONTROL])
    }

    @Test
    fun `check WebSocket interaction`() {
        val wsClient = WebSocketClient()
        wsClient.start()

        val closeLatch = CountDownLatch(1)

        val desiredCount = 100
        val list = mutableListOf<String>()

        val wsHandler = object : NoOpEndpoint() {

            override fun onWebSocketConnect(session: Session) {
                log.info("onWebSocketConnect : $session")
            }

            override fun onWebSocketClose(statusCode: Int, reason: String?) {
                log.info("onClose: $statusCode - $reason")
                closeLatch.countDown()
                super.onWebSocketClose(statusCode, reason)
            }

            override fun onWebSocketText(message: String) {
                list.add(message)
                if (list.size >= desiredCount) {
                    log.info("All received")
                    closeLatch.countDown()
                }
            }
        }

        /*
        val upgradeListener = object: UpgradeListener {
            override fun onHandshakeRequest(request: UpgradeRequest) {
                // Todo set basic auth headers
            }

            override fun onHandshakeResponse(response: UpgradeResponse) {

            }
        }
         */

        val uri = URI(
            "ws://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/" +
                    "${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/health/counterfeed"
        )

        log.info("Connecting to: $uri")

        val session = wsClient.connect(wsHandler, uri, ClientUpgradeRequest()/*, upgradeListener*/)
            .get(10, TimeUnit.SECONDS)
        log.info("Session established: $session")
        closeLatch.await()
        session.close(CloseStatus(StatusCode.NORMAL, "All items received. Thank you!"))
        wsClient.stop()

        val expectedContent = (0..99).map { "$it" }
        assertThat(list).containsAll(expectedContent)
    }
}