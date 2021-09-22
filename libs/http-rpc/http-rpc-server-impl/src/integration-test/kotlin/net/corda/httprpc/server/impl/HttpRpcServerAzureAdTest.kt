package net.corda.httprpc.server.impl

import kong.unirest.HttpStatus
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.security.read.impl.RPCSecurityManagerFactoryStubImpl
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.AzureAdSettings
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.config.models.SsoSettings
import net.corda.httprpc.server.impl.HttpRpcServerTestBase.Companion.serverPort
import net.corda.httprpc.server.impl.apigen.test.TestHealthCheckAPIImpl
import net.corda.httprpc.server.impl.utils.AzureAdMock
import net.corda.httprpc.server.impl.utils.TestHttpClient
import net.corda.httprpc.server.impl.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.server.impl.utils.WebRequest
import net.corda.v5.httprpc.tools.HttpVerb
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class HttpRpcServerAzureAdTest {
    private lateinit var httpRpcSettings: HttpRpcSettings
    private lateinit var httpRpcServer: HttpRpcServer
    private lateinit var client: TestHttpClient
    private lateinit var securityManager: RPCSecurityManager

    @BeforeEach
    fun setUp() {
//        val users = listOf(
//                User(AzureAdMock.username, "", setOf("InvokeRpc:net.corda.httprpc.test.TestHealthCheckAPI#void")),
//                User(AzureAdMock.clientId, "", setOf()))
        securityManager = RPCSecurityManagerFactoryStubImpl().createRPCSecurityManager()
        httpRpcSettings = HttpRpcSettings(NetworkHostAndPort("localhost", serverPort),
                HttpRpcContext("1", "api", "HttpRpcContext test title ", "HttpRpcContext test description"),
                null,
                SsoSettings(AzureAdSettings(AzureAdMock.clientId, null, AzureAdMock.tenantId, trustedIssuers = listOf(AzureAdMock.issuer))), HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE)
        httpRpcServer = HttpRpcServerImpl(listOf(TestHealthCheckAPIImpl()), securityManager, httpRpcSettings, true, ClassLoader.getSystemClassLoader()).apply { start() }
        client = TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
    }

    @AfterEach
    fun tearDown() {
        httpRpcServer.stop()
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

    private data class User(val username: String, val password: String, val permissions: Set<String>)
}
