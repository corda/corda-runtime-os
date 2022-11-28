package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.WebRequest
import net.corda.httprpc.test.utils.WebResponse
import net.corda.httprpc.test.utils.multipartDir
import net.corda.httprpc.tools.HttpVerb
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpRpcServerMaxContentLengthTest : HttpRpcServerTestBase() {
    companion object {
        private const val MAX_CONTENT_LENGTH = 100
        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            val httpRpcSettings = HttpRpcSettings(NetworkHostAndPort("localhost",  0), context, null,
                null, MAX_CONTENT_LENGTH, 20000L)
            server = HttpRpcServerImpl(
                listOf(TestHealthCheckAPIImpl()),
                ::securityManager,
                httpRpcSettings,
                multipartDir,
                true
            ).apply { start() }
            client = TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${server.port}/" +
                    "${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.close()
            }
        }
    }

    @Test
    fun `Content length exceeding maxContentLength returns Bad Request`() {

        val dataExceedsMax="1".repeat(MAX_CONTENT_LENGTH + 5)
        val pingResponse = client.call(HttpVerb.POST, WebRequest("health/ping", dataExceedsMax), userName, password)
        assertEquals(HttpStatus.SC_BAD_REQUEST, pingResponse.responseStatus)
        val actual = pingResponse.body
        assertNotNull(actual)
        assertTrue(actual.contains("Content length is ${MAX_CONTENT_LENGTH + 5} which exceeds the maximum limit of $MAX_CONTENT_LENGTH."))
    }

    @Test
    fun `Content length below maxContentLength returns 200`() {

        fun WebResponse<String>.doAssert() {
            assertEquals(HttpStatus.SC_OK, responseStatus)
            assertEquals("Pong for str = stringdata", body)
        }

        // Call with explicit "pingPongData" in the root JSON
        client.call(
            HttpVerb.POST,
            WebRequest("health/ping", """{"pingPongData": {"str": "stringdata"}}"""),
            userName,
            password
        ).doAssert()

        // Call without explicit "pingPongData" in the root JSON
        client.call(
            HttpVerb.POST,
            WebRequest("health/ping", """{"str": "stringdata"}"""), userName, password)
            .doAssert()
    }
}
