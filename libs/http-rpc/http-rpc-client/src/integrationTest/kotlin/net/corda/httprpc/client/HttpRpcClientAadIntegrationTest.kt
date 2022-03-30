package net.corda.httprpc.client

import net.corda.httprpc.client.config.HttpRpcClientConfig
import net.corda.httprpc.server.config.models.AzureAdSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.config.models.SsoSettings
import net.corda.httprpc.server.impl.HttpRpcServerImpl
import net.corda.httprpc.test.TestHealthCheckAPI
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utls.AzureAdMock
import net.corda.httprpc.test.utls.findFreePort
import net.corda.v5.base.util.NetworkHostAndPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

class HttpRpcClientAadIntegrationTest : HttpRpcIntegrationTestBase() {

    private lateinit var httpRpcSettings: HttpRpcSettings

    @BeforeEach
    fun setUp() {
        httpRpcSettings = HttpRpcSettings(
            NetworkHostAndPort("localhost", findFreePort()),
            context,
            null,
            SsoSettings(AzureAdSettings(AzureAdMock.clientId, null, AzureAdMock.tenantId, trustedIssuers = listOf(AzureAdMock.issuer))),
            HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE
        )
        server = HttpRpcServerImpl(
            listOf(TestHealthCheckAPIImpl()),
            securityManager,
            httpRpcSettings,
            multipartDir,
            true
        ).apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `azuread token accepted as authentication`() {
        AzureAdMock.create().use {
            val client = HttpRpcClient(
                baseAddress = "http://localhost:${httpRpcSettings.address.port}/api/v1/",
                TestHealthCheckAPI::class.java,
                HttpRpcClientConfig()
                    .enableSSL(false)
                    .minimumServerProtocolVersion(1)
                    .bearerToken { AzureAdMock.generateUserToken() },
                healthCheckInterval = 500
            )

            client.use {
                val connection = client.start()

                with(connection.proxy) {
                    assertEquals(""""Pong for str = value"""", this.ping(TestHealthCheckAPI.PingPongData("value")))
                }
            }
        }
    }
}