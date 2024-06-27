package net.corda.rest.server.impl

import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.test.TestEndpointVersioningRestResourceImpl
import net.corda.rest.test.TestResourceMaxVersioningRestResourceImpl
import net.corda.rest.test.TestResourceVersioningRestResourceImpl
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.WebRequest
import net.corda.rest.test.utils.multipartDir
import net.corda.rest.tools.HttpVerb
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RestServerApiVersioningTest : RestServerTestBase() {
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
                listOf(
                    TestEndpointVersioningRestResourceImpl(),
                    TestResourceVersioningRestResourceImpl(),
                    TestResourceMaxVersioningRestResourceImpl()
                ),
                RestServerTestBase.Companion::securityManager,
                restServerSettings,
                multipartDir,
                true
            ).apply { start() }
            client =
                TestHttpClientUnirestImpl(
                    "http://${restServerSettings.address.host}:${server.port}/" +
                        "${restServerSettings.context.basePath}/"
                )
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
    fun `same endpoint available in multiple versions`() {
        val response = client.call(
            HttpVerb.GET,
            WebRequest<Any>("${RestApiVersion.C5_2.versionPath}/testEndpointVersion/1234"),
            userName,
            password
        )

        val response2 = client.call(
            HttpVerb.GET,
            WebRequest<Any>("${RestApiVersion.C5_3.versionPath}/testEndpointVersion/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(HttpStatus.SC_OK, response2.responseStatus)
    }

    @Test
    fun `endpoint added at a particular version`() {
        val response = client.call(
            HttpVerb.GET,
            WebRequest<Any>("${RestApiVersion.C5_1.versionPath}/testEndpointVersion/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_NOT_FOUND, response.responseStatus)

        val response2 = client.call(
            HttpVerb.GET,
            WebRequest<Any>("${RestApiVersion.C5_3.versionPath}/testEndpointVersion/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response2.responseStatus)
    }

    @Test
    fun `endpoint removed at a particular version`() {
        val response = client.call(
            HttpVerb.GET,
            WebRequest<Any>("${RestApiVersion.C5_1.versionPath}/testEndpointVersion?id=1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)

        val response2 = client.call(
            HttpVerb.GET,
            WebRequest<Any>("${RestApiVersion.C5_2.versionPath}/testEndpointVersion?id=1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_NOT_FOUND, response2.responseStatus)
    }

    @Test
    fun `request works with resource versions when no version specified at endpoint level`() {
        val response = client.call(
            HttpVerb.GET,
            WebRequest<Any>("${RestApiVersion.C5_2.versionPath}/testResourceVersion?id=1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
    }

    @Test
    fun `when endpoint versions are outside of resource version limit, calling endpoint fails`() {
        val response = client.call(
            HttpVerb.GET,
            WebRequest<Any>("${RestApiVersion.C5_1.versionPath}/testResourceVersion/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_NOT_FOUND, response.responseStatus)
    }

    @Test
    fun `endpoint without specified maxVersion supported up to CURRENT Rest Endpoint version`() {
        val response = client.call(
            HttpVerb.GET,
            WebRequest<Any>("${RestApiVersion.C5_1.versionPath}/testResourceMaxVersion/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)

        val response2 = client.call(
            HttpVerb.GET,
            WebRequest<Any>("${RestApiVersion.C5_2.versionPath}/testResourceMaxVersion/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response2.responseStatus)
    }
}
