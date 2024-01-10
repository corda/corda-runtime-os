package net.corda.rest.server.impl

import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.test.CustomNonSerializableString
import net.corda.rest.test.CustomUnsafeString
import net.corda.rest.test.ResponseEntityRestResourceImpl
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.WebRequest
import net.corda.rest.test.utils.multipartDir
import net.corda.rest.tools.HttpVerb.DELETE
import net.corda.rest.tools.HttpVerb.POST
import net.corda.rest.tools.HttpVerb.PUT
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RestServerResponseEntityTest : RestServerTestBase() {
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
                    ResponseEntityRestResourceImpl()
                ),
                ::securityManager,
                restServerSettings,
                multipartDir,
                true
            ).apply { start() }
            client =
                TestHttpClientUnirestImpl(
                    "http://${restServerSettings.address.host}:${server.port}/" +
                        "${restServerSettings.context.basePath}/${apiVersion.versionPath}/"
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
    fun `endpoint returns ResponseEntity with null responseBody returns null in json with given status code`() {
        val response = client.call(PUT, WebRequest<Any>("responseentity/put-returns-nullable-string"), userName, password)
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals("null", response.body)
    }

    @Test
    fun `endpoint returns ResponseEntity with responseBody and given status code`() {
        val response = client.call(PUT, WebRequest<Any>("responseentity/put-returns-ok-string"), userName, password)
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals("some string that isn't json inside response", response.body)
    }

    @Test
    fun `endpoint returns string result the same as if using ResponseEntity with OK status`() {
        val response = client.call(PUT, WebRequest<Any>("responseentity/put-returns-string"), userName, password)
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals("put string", response.body)
    }

    @Test
    fun `endpoint returns an object the same as using ResponseEntity with OK status`() {
        val response = client.call(POST, WebRequest<Any>("responseentity/post-returns-raw-entity"), userName, password)
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals("{\"id\":\"no response entity used\"}", response.body)
    }

    @Test
    fun `endpoint with no return type completes with no_content status`() {
        val response = client.call(DELETE, WebRequest<Any>("responseentity/delete-returns-void"), userName, password)
        assertEquals(HttpStatus.SC_NO_CONTENT, response.responseStatus)
        assertEquals("", response.body)
    }

    @Test
    fun `post returns void has no_content and empty body`() {
        val response = client.call(POST, WebRequest<Any>("responseentity/post-returns-void"), userName, password)
        assertEquals(HttpStatus.SC_NO_CONTENT, response.responseStatus)
        assertEquals("", response.body)
    }

    @Test
    fun `post returns a string and has status OK with the string in the response body`() {
        val response = client.call(POST, WebRequest<Any>("responseentity/post-returns-ok-string-json"), userName, password)
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals("{\"somejson\": \"for confusion\"}", response.body)
    }

    @Test
    fun `put returning void has no_content and empty body`() {
        val response = client.call(PUT, WebRequest<Any>("responseentity/put-returns-void"), userName, password)
        assertEquals(HttpStatus.SC_NO_CONTENT, response.responseStatus)
        assertEquals("", response.body)
    }

    @Test
    fun `async api returning status code accepted`() {
        val response = client.call(DELETE, WebRequest<Any>("responseentity/async-delete-returns-accepted"), userName, password)
        assertEquals(HttpStatus.SC_ACCEPTED, response.responseStatus)
        assertEquals("\"DELETING\"", response.body)
    }
}
