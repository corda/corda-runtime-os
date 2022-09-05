package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.HttpRpcSSLSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.ssl.impl.SslCertReadServiceStubImpl
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.findFreePort
import net.corda.httprpc.test.utils.multipartDir
import net.corda.base.util.NetworkHostAndPort
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

        lateinit var httpRpcSettings: HttpRpcSettings

        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            //System.setProperty("javax.net.debug", "all")
            val keyStoreInfo = sslService.getOrCreateKeyStore()
            val sslConfig = HttpRpcSSLSettings(keyStoreInfo.path, keyStoreInfo.password)

            httpRpcSettings = HttpRpcSettings(
                NetworkHostAndPort("localhost", findFreePort()),
                context,
                sslConfig,
                null,
                HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE
            )

            server = HttpRpcServerImpl(
                listOf(
                    TestHealthCheckAPIImpl()
                ),
                ::securityManager,
                httpRpcSettings,
                multipartDir,
                true
            ).apply { start() }
            client = TestHttpClientUnirestImpl(
                "https://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/",
                true
            )
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.stop()
            }
        }
    }

    override fun createWsClient() = WebSocketClient(HttpClient(SslContextFactory.Client(true)))

    override val wsProtocol = "wss"

    override val log = LOG

    override val httpRpcSettings = HttpsRpcServerWebsocketTest.httpRpcSettings
}