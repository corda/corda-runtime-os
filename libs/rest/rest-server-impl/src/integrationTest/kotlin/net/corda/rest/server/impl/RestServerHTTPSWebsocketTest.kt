package net.corda.rest.server.impl

import net.corda.rest.server.config.models.RestSSLSettings
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.ssl.impl.SslCertReadServiceImpl
import net.corda.rest.test.TestHealthCheckAPIImpl
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.multipartDir
import net.corda.utilities.NetworkHostAndPort
import org.slf4j.LoggerFactory
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.mockito.kotlin.mock
import java.nio.file.Files

class RestServerHTTPSWebsocketTest : AbstractWebsocketTest() {
    private companion object {

        val LOG = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private val sslService = SslCertReadServiceImpl {
            Files.createTempDirectory("RestServerHTTPSWebsocketTest")
        }

        lateinit var restServerSettings: RestServerSettings

        @BeforeAll
        @JvmStatic
        @Suppress("unused")
        fun setUpBeforeClass() {
            //System.setProperty("javax.net.debug", "all")
            val keyStoreInfo = sslService.getOrCreateKeyStoreInfo(mock())
            val sslConfig = RestSSLSettings(keyStoreInfo.path, keyStoreInfo.password)

            restServerSettings = RestServerSettings(
                NetworkHostAndPort("localhost", 0),
                context,
                sslConfig,
                null,
                RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
                20000L
            )

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
                "https://${restServerSettings.address.host}:${server.port}/${restServerSettings.context.basePath}/v${restServerSettings.context.version}/",
                true
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

    override fun createWsClient() = WebSocketClient(HttpClient(SslContextFactory.Client(true)))

    override val wsProtocol = "wss"

    override val log = LOG

    override val restServerSettings = RestServerHTTPSWebsocketTest.restServerSettings

    override val port: Int
        get() = server.port
}
