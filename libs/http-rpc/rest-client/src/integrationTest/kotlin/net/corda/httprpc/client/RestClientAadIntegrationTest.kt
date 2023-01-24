package net.corda.httprpc.client

import net.corda.httprpc.client.config.RestClientConfig
import net.corda.httprpc.server.config.models.AzureAdSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.config.models.SsoSettings
import net.corda.httprpc.server.impl.HttpRpcServerImpl
import net.corda.httprpc.test.TestHealthCheckAPI
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.AzureAdMock
import net.corda.httprpc.test.utils.multipartDir
import net.corda.utilities.NetworkHostAndPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RestClientAadIntegrationTest : RestIntegrationTestBase() {

    @BeforeEach
    fun setUp() {
        val httpRpcSettings = HttpRpcSettings(
            NetworkHostAndPort("localhost", 0),
            context,
            null,
            SsoSettings(AzureAdSettings(AzureAdMock.clientId, null, AzureAdMock.tenantId, trustedIssuers = listOf(AzureAdMock.issuer))),
            HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
            20000L
        )
        server = HttpRpcServerImpl(
            listOf(TestHealthCheckAPIImpl()),
            ::securityManager,
            httpRpcSettings,
            multipartDir,
            true
        ).apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `azuread token accepted as authentication`() {
        AzureAdMock.create().use {
            val client = RestClient(
                baseAddress = "http://localhost:${server.port}/api/v1/",
                TestHealthCheckAPI::class.java,
                RestClientConfig()
                    .enableSSL(false)
                    .minimumServerProtocolVersion(1)
                    .bearerToken { AzureAdMock.generateUserToken() },
                healthCheckInterval = 500
            )

            client.use {
                val connection = client.start()

                with(connection.proxy) {
                    assertEquals("Pong for str = value", this.ping(TestHealthCheckAPI.PingPongData("value")))
                }
            }
        }
    }
}
