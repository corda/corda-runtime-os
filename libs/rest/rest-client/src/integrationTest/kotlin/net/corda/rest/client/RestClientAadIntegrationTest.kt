package net.corda.rest.client

import net.corda.rest.client.config.RestClientConfig
import net.corda.rest.server.config.models.AzureAdSettings
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.server.config.models.SsoSettings
import net.corda.rest.server.impl.RestServerImpl
import net.corda.rest.test.TestHealthCheckAPI
import net.corda.rest.test.TestHealthCheckAPIImpl
import net.corda.rest.test.utils.AzureAdMock
import net.corda.rest.test.utils.multipartDir
import net.corda.utilities.NetworkHostAndPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RestClientAadIntegrationTest : RestIntegrationTestBase() {

    @BeforeEach
    fun setUp() {
        val restServerSettings = RestServerSettings(
            NetworkHostAndPort("localhost", 0),
            context,
            null,
            SsoSettings(AzureAdSettings(AzureAdMock.clientId, null, AzureAdMock.tenantId, trustedIssuers = listOf(AzureAdMock.issuer))),
            RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
            20000L
        )
        server = RestServerImpl(
            listOf(TestHealthCheckAPIImpl()),
            ::securityManager,
            restServerSettings,
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
