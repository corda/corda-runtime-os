package net.corda.httprpc.server.impl

import kong.unirest.HttpStatus
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.AzureAdSettings
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.config.models.HttpRpcSettings
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
import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail


class HttpRpcServerAzureAdTest {

    private companion object {
        val log = contextLogger()
    }

    private lateinit var httpRpcServer: HttpRpcServer
    private lateinit var client: TestHttpClient
    private lateinit var securityManager: RPCSecurityManager

    @BeforeEach
    fun setUp() {
        securityManager = FakeSecurityManager()
        val httpRpcSettings = HttpRpcSettings(
            NetworkHostAndPort("localhost", 0),
            HttpRpcContext("1", "api", "HttpRpcContext test title ", "HttpRpcContext test description"),
            null,
            SsoSettings(
                AzureAdSettings(AzureAdMock.clientId, null, AzureAdMock.tenantId, trustedIssuers = listOf(AzureAdMock.issuer))
            ),
            HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
            20000L
        )
        httpRpcServer = HttpRpcServerImpl(
            listOf(TestHealthCheckAPIImpl()),
            ::securityManager,
            httpRpcSettings,
            multipartDir,
            true
        ).apply { start() }
        client = TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${httpRpcServer.port}/" +
                "${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
    }

    @AfterEach
    fun tearDown() {
        httpRpcServer.close()
    }

    @Test
    fun `Authentication is successful with AzureAd access token`() {
        log.info("Starting: Authentication is successful with AzureAd access token")
        val mock = AzureAdMock.create()
        log.info("Mock created")
        mock.use {
            val token = AzureAdMock.generateUserToken()
            log.info("Token generated")
            val getPathResponse = client.call(HttpVerb.GET, WebRequest<Any>("health/sanity"), token)
            log.info("Remote call performed")
            assertEquals(HttpStatus.OK, getPathResponse.responseStatus)
        }
        fail("Deliberate failure to capture logs")
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
