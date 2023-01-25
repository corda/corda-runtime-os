package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.RestSSLSettings
import net.corda.httprpc.server.config.models.RestServerSettings
import net.corda.httprpc.ssl.impl.SslCertReadServiceStubImpl
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.multipartDir
import net.corda.utilities.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.nio.file.Files

class HttpsRpcServerWebsocketTest : AbstractWebsocketTest() {
    private companion object {

        val LOG = contextLogger()

        private val sslService = SslCertReadServiceStubImpl {
            Files.createTempDirectory("HttpsRpcServerWebsocketTest")
        }

        lateinit var restServerSettings: RestServerSettings

        @BeforeAll
        @JvmStatic
        @Suppress("unused")
        fun setUpBeforeClass() {
            //System.setProperty("javax.net.debug", "all")
            val keyStoreInfo = sslService.getOrCreateKeyStore()
            val sslConfig = RestSSLSettings(keyStoreInfo.path, keyStoreInfo.password)

            restServerSettings = RestServerSettings(
                NetworkHostAndPort("localhost", 0),
                context,
                sslConfig,
                null,
                RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
                20000L
            )

            server = HttpRpcServerImpl(
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

    override val restServerSettings = HttpsRpcServerWebsocketTest.restServerSettings

    override val port: Int
        get() = server.port
}
