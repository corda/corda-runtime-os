package net.corda.rest.server.impl

import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.test.TestHealthCheckAPIImpl
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.multipartDir
import net.corda.utilities.NetworkHostAndPort
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.slf4j.LoggerFactory

class RestServerWebsocketTest : AbstractWebsocketTest() {
    private companion object {

        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

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
            client = TestHttpClientUnirestImpl(
                "http://${restServerSettings.address.host}:${server.port}/" +
                    "${restServerSettings.context.basePath}/${apiVersion.versionPath}/"
            )
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
