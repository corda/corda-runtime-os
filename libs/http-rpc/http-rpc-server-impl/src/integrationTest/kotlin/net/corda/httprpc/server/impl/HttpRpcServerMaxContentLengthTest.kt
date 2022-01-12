package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.server.impl.utils.WebRequest
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.v5.base.util.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HttpRpcServerMaxContentLengthTest : HttpRpcServerTestBase() {
    companion object {
        private const val MAX_CONTENT_LENGTH = 100
        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            val httpRpcSettings = HttpRpcSettings(NetworkHostAndPort("localhost",  findFreePort()), context, null, null, MAX_CONTENT_LENGTH)
            server = HttpRpcServerImpl(listOf(TestHealthCheckAPIImpl()), securityManager, httpRpcSettings, true).apply { start() }
            client = TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.stop()
            }
        }
    }

    @Test
    fun `Content length exceeding maxContentLength returns Bad Request`() {

        val dataExceedsMax="1".repeat(MAX_CONTENT_LENGTH + 5)
        val pingResponse = client.call(net.corda.httprpc.tools.HttpVerb.POST, WebRequest("health/ping", dataExceedsMax), userName, password)
        assertEquals(HttpStatus.SC_BAD_REQUEST, pingResponse.responseStatus)
        assertNotNull(pingResponse.body)
        assertTrue(pingResponse.body.contains("Content length is ${MAX_CONTENT_LENGTH + 5} which exceeds the maximum limit of $MAX_CONTENT_LENGTH."))
    }

    @Test
    fun `Content length below maxContentLength returns 200`() {

        val pingResponse = client.call(net.corda.httprpc.tools.HttpVerb.POST, WebRequest("health/ping", """{"pingPongData": {"str": "stringdata"}}"""), userName, password)
        assertEquals(HttpStatus.SC_OK, pingResponse.responseStatus)
        assertEquals(""""Pong for str = stringdata"""", pingResponse.body)
    }
}
