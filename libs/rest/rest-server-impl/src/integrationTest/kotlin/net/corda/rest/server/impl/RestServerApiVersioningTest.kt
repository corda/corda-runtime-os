package net.corda.rest.server.impl

import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.test.CustomNonSerializableString
import net.corda.rest.test.CustomUnsafeString
import net.corda.rest.test.TestVersioningRestResourceImpl
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.WebRequest
import net.corda.rest.test.utils.multipartDir
import net.corda.rest.tools.HttpVerb
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
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
                    TestVersioningRestResourceImpl()
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

    @AfterEach
    fun reset() {
        CustomUnsafeString.flag = false
        CustomNonSerializableString.flag = false
        securityManager.forgetChecks()
    }

    @Test
    fun `calling path in non supported version fails`() {

        val response = client.call(
            HttpVerb.GET, WebRequest<Any>("${RestApiVersion.C5_0.versionPath}/testEntity/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_NOT_FOUND, response.responseStatus)
    }

    @Test
    fun `calling path in supported version`() {

        val response = client.call(
            HttpVerb.GET, WebRequest<Any>("${RestApiVersion.C5_2.versionPath}/testEntity/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals("Retrieved using path id: 1234", response.body)
    }

    @Test
    fun `endpoint returns ResponseEntity with null responseBody returns null in json with given status code2`() {

        val response = client.call(
            HttpVerb.GET, WebRequest<Any>("${RestApiVersion.C5_0.versionPath}/testEntity?id=1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals("Retrieved using query: 1234", response.body)
    }

    @Test
    fun `same endpoint available in multiple versions`() {
        val response = client.call(
            HttpVerb.GET, WebRequest<Any>("${RestApiVersion.C5_1.versionPath}/testEntity/1234"),
            userName,
            password
        )

        val response2 = client.call(
            HttpVerb.GET, WebRequest<Any>("${RestApiVersion.C5_2.versionPath}/testEntity/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(HttpStatus.SC_OK, response2.responseStatus)
    }

    @Test
    fun `endpoint added at a particular version`() {
        val response = client.call(
            HttpVerb.GET, WebRequest<Any>("${RestApiVersion.C5_0.versionPath}/testEntity/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_NOT_FOUND, response.responseStatus)

        val response2 = client.call(
            HttpVerb.GET, WebRequest<Any>("${RestApiVersion.C5_2.versionPath}/testEntity/1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response2.responseStatus)
    }

    @Test
    fun `endpoint removed at a particular version`() {
        val response = client.call(
            HttpVerb.GET, WebRequest<Any>("${RestApiVersion.C5_0.versionPath}/testEntity?id=1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)

        val response2 = client.call(
            HttpVerb.GET, WebRequest<Any>("${RestApiVersion.C5_0.versionPath}/testEntity?id=1234"),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_NOT_FOUND, response2.responseStatus)
    }



}