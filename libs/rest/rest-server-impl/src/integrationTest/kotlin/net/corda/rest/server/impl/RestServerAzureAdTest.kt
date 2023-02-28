package net.corda.rest.server.impl

import kong.unirest.HttpStatus
import net.corda.rest.security.read.RestSecurityManager
import net.corda.rest.server.RestServer
import net.corda.rest.server.config.models.AzureAdSettings
import net.corda.rest.server.config.models.RestContext
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.server.config.models.SsoSettings
import net.corda.rest.test.TestHealthCheckAPIImpl
import net.corda.rest.test.utils.AzureAdMock
import net.corda.rest.test.utils.FakeSecurityManager
import net.corda.rest.test.utils.TestHttpClient
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.WebRequest
import net.corda.rest.test.utils.multipartDir
import net.corda.rest.tools.HttpVerb
import net.corda.utilities.NetworkHostAndPort
import org.slf4j.LoggerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class RestServerAzureAdTest {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private lateinit var restServer: RestServer
    private lateinit var client: TestHttpClient
    private lateinit var securityManager: RestSecurityManager

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
        restServer = RestServerImpl(
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
