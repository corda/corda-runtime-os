package net.corda.rest.server.impl

import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.test.TestHealthCheckAPIImpl
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.WebRequest
import net.corda.rest.test.utils.multipartDir
import net.corda.rest.tools.HttpVerb.GET
import net.corda.rest.tools.HttpVerb.POST
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RestServerCaseSensitiveUrlTest: RestServerTestBase() {
    companion object {

        @BeforeAll
        @JvmStatic
        @Suppress("Unused")
        fun setUpBeforeClass() {
            val restServerSettings = RestServerSettings(NetworkHostAndPort("localhost", 0), context, null,
                null, RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE, 20000L)
            server = RestServerImpl(
                listOf(TestHealthCheckAPIImpl()),
                ::securityManager,
                restServerSettings,
                multipartDir,
                true
            ).apply { start() }
            client = TestHttpClientUnirestImpl("http://${restServerSettings.address.host}:${server.port}/" +
                    "${restServerSettings.context.basePath}/v${restServerSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        @Suppress("Unused")
        fun cleanUpAfterClass() {
            server.close()
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
