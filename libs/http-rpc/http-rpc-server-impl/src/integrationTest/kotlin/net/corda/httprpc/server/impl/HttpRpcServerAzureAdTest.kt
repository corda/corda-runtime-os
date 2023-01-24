package net.corda.httprpc.server.impl

import kong.unirest.HttpStatus
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.RestServer
import net.corda.httprpc.server.config.models.AzureAdSettings
import net.corda.httprpc.server.config.models.RestContext
import net.corda.httprpc.server.config.models.RestServerSettings
import net.corda.httprpc.server.config.models.SsoSettings
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.AzureAdMock
import net.corda.httprpc.test.utils.FakeSecurityManager
import net.corda.httprpc.test.utils.TestHttpClient
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.WebRequest
import net.corda.httprpc.test.utils.multipartDir
import net.corda.httprpc.tools.HttpVerb
import net.corda.utilities.NetworkHostAndPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class HttpRpcServerAzureAdTest {
    private lateinit var restServer: RestServer
    private lateinit var client: TestHttpClient
    private lateinit var securityManager: RPCSecurityManager

    @BeforeEach
    fun setUp() {
        securityManager = FakeSecurityManager()
        val restServerSettings = RestServerSettings(
            NetworkHostAndPort("localhost", 0),
            RestContext("1", "api", "RestContext test title ", "RestContext test description"),
            null,
            SsoSettings(
                AzureAdSettings(AzureAdMock.clientId, null, AzureAdMock.tenantId, trustedIssuers = listOf(AzureAdMock.issuer))
            ),
            RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
            20000L
        )
        restServer = HttpRpcServerImpl(
            listOf(TestHealthCheckAPIImpl()),
            ::securityManager,
            restServerSettings,
            multipartDir,
            true
        ).apply { start() }
        client = TestHttpClientUnirestImpl("http://${restServerSettings.address.host}:${restServer.port}/" +
                "${restServerSettings.context.basePath}/v${restServerSettings.context.version}/")
    }

    @AfterEach
    fun tearDown() {
        restServer.close()
    }

    @Test
    fun `Authentication is successful with AzureAd access token`() {
        AzureAdMock.create().use {
            val token = AzureAdMock.generateUserToken()
            val getPathResponse = client.call(HttpVerb.GET, WebRequest<Any>("health/sanity"), token)

            assertEquals(HttpStatus.OK, getPathResponse.responseStatus)
        }
    }

    @Test
    fun `Authentication is successful with AzureAd client-only access token`() {
        AzureAdMock.create().use {
            val token = AzureAdMock.generateApplicationToken()
            val getPathResponse = client.call(HttpVerb.GET, WebRequest<Any>("health/sanity"), token)

            assertEquals(HttpStatus.OK, getPathResponse.responseStatus)
        }
    }
}
