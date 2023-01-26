package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.RestServerSettings
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.multipartDir
import net.corda.utilities.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class RestServerWebsocketTest : AbstractWebsocketTest() {
    private companion object {

        val log = contextLogger()

        private val restServerSettings = RestServerSettings(
            NetworkHostAndPort("localhost", 0),
            context,
            null,
            null,
            RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
            20000L
        )

        @BeforeAll
        @JvmStatic
        @Suppress("unused")
        fun setUpBeforeClass() {
            server = RestServerImpl(
                listOf(
                    TestHealthCheckAPIImpl()
                ),
                ::securityManager,
                restServerSettings,
                multipartDir,
                true
            ).apply { start() }
            client = TestHttpClientUnirestImpl("http://${restServerSettings.address.host}:${server.port}/" +
                    "${restServerSettings.context.basePath}/v${restServerSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        @Suppress("unused")
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.close()
            }
        }
    }

    override fun createWsClient(): WebSocketClient = WebSocketClient()

    override val wsProtocol = "ws"

    override val log = RestServerWebsocketTest.log

    override val restServerSettings = RestServerWebsocketTest.restServerSettings

    override val port: Int
        get() = server.port
}
