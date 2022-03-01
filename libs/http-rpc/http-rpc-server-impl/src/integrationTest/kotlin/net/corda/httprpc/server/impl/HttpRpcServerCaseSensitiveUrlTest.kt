package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.server.impl.utils.WebRequest
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.tools.HttpVerb.GET
import net.corda.httprpc.tools.HttpVerb.POST
import net.corda.v5.base.util.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HttpRpcServerCaseSensitiveUrlTest: HttpRpcServerTestBase() {
    companion object {

        @BeforeAll
        @JvmStatic
        @Suppress("Unused")
        fun setUpBeforeClass() {
            val httpRpcSettings = HttpRpcSettings(NetworkHostAndPort("localhost", findFreePort()), context, null, null, HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE)
            server = HttpRpcServerImpl(listOf(TestHealthCheckAPIImpl()), securityManager, httpRpcSettings, true).apply { start() }
            client = TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        @Suppress("Unused")
        fun cleanUpAfterClass() {
            server.stop()
        }
    }

    @Test
    fun `Uppercase GET will redirect the request`() {

        val plusOneResponse = client.call(GET, WebRequest<Any>("health/Plusone",
            queryParameters = mapOf("numbers" to listOf(1.0, 2.0))), List::class.java, userName, password)
        assertEquals(HttpStatus.SC_OK, plusOneResponse.responseStatus)
        assertEquals(listOf(2.0, 3.0), plusOneResponse.body)
    }

    @Test
    fun `Uppercase POST will return 301 Moved Permanently`() {

        val pingResponse = client.call(POST, WebRequest("health/Ping",
            """{"pingPongData": {"str": "stringdata"}}"""), userName, password)
        assertEquals(HttpStatus.SC_MOVED_PERMANENTLY, pingResponse.responseStatus)
    }

    @Test
    fun `GET mixed case of query param`() {
        val requestStringValue = "MyRequestString"
        val plusOneResponse = client.call(GET, WebRequest<Any>("health/EchoQuery",
            queryParameters = mapOf("RequestString" to requestStringValue)), String::class.java, userName, password)
        assertEquals(HttpStatus.SC_OK, plusOneResponse.responseStatus)
        assertEquals(requestStringValue, plusOneResponse.body)
    }

    @Test
    fun `GET mixed case of path param`() {
        val requestStringValue = "MyRequestString"
        val plusOneResponse = client.call(GET, WebRequest<Any>("health/EchoPath/$requestStringValue"),
            String::class.java, userName, password)
        assertEquals(HttpStatus.SC_OK, plusOneResponse.responseStatus)
        assertEquals(requestStringValue, plusOneResponse.body)
    }
}