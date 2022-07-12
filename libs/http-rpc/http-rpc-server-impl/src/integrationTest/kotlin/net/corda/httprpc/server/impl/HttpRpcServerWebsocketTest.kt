package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.findFreePort
import net.corda.httprpc.test.utils.multipartDir
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll

class HttpRpcServerWebsocketTest : AbstractWebsocketTest() {
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

    override fun createWsClient(): WebSocketClient = WebSocketClient()

    override val wsProtocol = "ws"

    override val log = HttpRpcServerWebsocketTest.log

    override val httpRpcSettings = HttpRpcServerWebsocketTest.httpRpcSettings
}