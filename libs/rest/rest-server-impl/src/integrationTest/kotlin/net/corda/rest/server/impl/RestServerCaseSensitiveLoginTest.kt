package net.corda.rest.server.impl

import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.test.TestHealthCheckAPIImpl
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.WebRequest
import net.corda.rest.test.utils.multipartDir
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RestServerCaseSensitiveLoginTest: RestServerTestBase() {
    companion object {

        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            val restServerSettings = RestServerSettings(
                NetworkHostAndPort("localhost", 0),
                context,
                null,
                null,
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
            client =
                TestHttpClientUnirestImpl("http://${restServerSettings.address.host}:" +
                        "${server.port}/${restServerSettings.context.basePath}/v${restServerSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            server.close()
        }
    }

    @Test
    fun `mixed case login works fine`() {
        val plusOneResponse = client.call(
            net.corda.rest.tools.HttpVerb.GET,
            WebRequest<Any>("health/plusone", queryParameters = mapOf("numbers" to listOf(1.0, 2.0))),
            List::class.java,
            "aDmIn",
            password
        )
        assertEquals(HttpStatus.SC_OK, plusOneResponse.responseStatus)
        assertEquals(listOf(2.0, 3.0), plusOneResponse.body)
    }

    @Test
    fun `mixed case password fails`() {
        val plusOneResponse = client.call(
            net.corda.rest.tools.HttpVerb.GET,
            WebRequest<Any>("health/plusone", queryParameters = mapOf("numbers" to listOf(1.0, 2.0))),
            List::class.java,
            userName,
            "aDmIn"
        )
        assertEquals(HttpStatus.SC_UNAUTHORIZED, plusOneResponse.responseStatus)
    }
}
