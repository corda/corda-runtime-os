package net.corda.rest.server.impl

import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.test.CustomNonSerializableString
import net.corda.rest.test.CustomUnsafeString
import net.corda.rest.test.ObjectsInJsonEndpointImpl
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.WebRequest
import net.corda.rest.test.utils.multipartDir
import net.corda.rest.tools.HttpVerb.POST
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RestServerJsonObjectTest : RestServerTestBase() {
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
                    ObjectsInJsonEndpointImpl()
                ),
                ::securityManager,
                restServerSettings,
                multipartDir,
                true
            ).apply { start() }
            client = TestHttpClientUnirestImpl(
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

    private val stringEscapedObjectPayload = """
        {
            "id": "aaa123",
            "obj": "{\"message\":\"Hey Mars\",\"planetaryOnly\":\"true\",\"target\":\"C=GB, L=FOURTH, O=MARS, OU=PLANET\"}"
        }
    """.trimIndent()

    private val realJsonObjectPayload = """
        {
            "id": "aaa123",
            "obj": {"message":"Hey Mars","planetaryOnly":"true","target":"C=GB, L=FOURTH, O=MARS, OU=PLANET"}
        }
    """.trimIndent()

    private val expectedObjectResponse = """
        {"id":"aaa123","obj":{"message":"Hey Mars","planetaryOnly":"true","target":"C=GB, L=FOURTH, O=MARS, OU=PLANET"}}
    """.trimIndent()

    private val stringEscapedMapPayload = """
        {
            "id": "aaa123",
            "obj": "{\"1\":{\"message\":\"Hey Mars\",\"planetaryOnly\":\"true\",\"target\":\"C=GB, L=FOURTH, O=MARS, OU=PLANET\"},\"2\":{\"message\":\"Hey Pluto\",\"planetaryOnly\":\"false\",\"target\":\"C=GB, L=FOURTH, O=PLUTO, OU=NON_PLANET\"}}"
        }
    """.trimIndent()

    private val realJsonMap = """
        {
            "id": "aaa123",
            "obj": {
                "1": {
                    "message":"Hey Mars",
                    "planetaryOnly":"true",
                    "target":"C=GB, L=FOURTH, O=MARS, OU=PLANET"
                },
                "2": {
                    "message":"Hey Pluto",
                    "planetaryOnly":"false",
                    "target":"C=GB, L=FOURTH, O=PLUTO, OU=NON_PLANET"
                }
            }
        }
    """.trimIndent()

    private val expectedMapResponse = """
        {"id":"aaa123","obj":{"1":{"message":"Hey Mars","planetaryOnly":"true","target":"C=GB, L=FOURTH, O=MARS, OU=PLANET"},"2":{"message":"Hey Pluto","planetaryOnly":"false","target":"C=GB, L=FOURTH, O=PLUTO, OU=NON_PLANET"}}}
    """.trimIndent()

    private val stringEscapedArrayPayload = """
        {
            "id": "aaa123",
            "obj": "[{\"message\":\"Hey Mars\",\"planetaryOnly\":\"true\",\"target\":\"C=GB, L=FOURTH, O=MARS, OU=PLANET\"},{\"message\":\"Hey Pluto\",\"planetaryOnly\":\"false\",\"target\":\"C=GB, L=FOURTH, O=PLUTO, OU=NON_PLANET\"}]"
        }
    """.trimIndent()

    private val realJsonArrayPayload = """
        {
          "id": "aaa123",
          "obj": [
            {
              "message": "Hey Mars",
              "planetaryOnly": "true",
              "target": "C=GB, L=FOURTH, O=MARS, OU=PLANET"
            },
            {
              "message": "Hey Pluto",
              "planetaryOnly": "false",
              "target": "C=GB, L=FOURTH, O=PLUTO, OU=NON_PLANET"
            }
          ]
        }
    """.trimIndent()

    private val expectedArrayResponse = """
        {"id":"aaa123","obj":[{"message":"Hey Mars","planetaryOnly":"true","target":"C=GB, L=FOURTH, O=MARS, OU=PLANET"},{"message":"Hey Pluto","planetaryOnly":"false","target":"C=GB, L=FOURTH, O=PLUTO, OU=NON_PLANET"}]}
    """.trimIndent()

    @Test
    fun `test escaped string serialization with api taking object`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-one-object",
                stringEscapedObjectPayload
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedObjectResponse, response.body)
    }

    @Test
    fun `test real json object serialization with api taking object`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-one-object",
                realJsonObjectPayload
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedObjectResponse, response.body)
    }

    @Test
    fun `test escaped string serialization with api taking individual parameters`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-individual-params",
                stringEscapedObjectPayload
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedObjectResponse, response.body)
    }

    @Test
    fun `test real json object serialization with api taking individual parameters`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-individual-params",
                realJsonObjectPayload
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedObjectResponse, response.body)
    }

    @Test
    fun `test real json map serialization with api taking object`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-one-object",
                realJsonMap
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedMapResponse, response.body)
    }

    @Test
    fun `test real json map serialization with api taking single parameters`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-individual-params",
                realJsonMap
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedMapResponse, response.body)
    }

    @Test
    fun `test string escaped map serialization with api taking object`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-one-object",
                stringEscapedMapPayload
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedMapResponse, response.body)
    }

    @Test
    fun `test string escaped map serialization with api taking single parameters`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-individual-params",
                stringEscapedMapPayload
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedMapResponse, response.body)
    }

    @Test
    fun `test string escaped array serialization with api taking object`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-one-object",
                stringEscapedArrayPayload
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedArrayResponse, response.body)
    }

    @Test
    fun `test string escaped array serialization with api taking single parameters`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-individual-params",
                stringEscapedArrayPayload
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedArrayResponse, response.body)
    }

    @Test
    fun `test real json array serialization with api taking object`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-one-object",
                realJsonArrayPayload
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedArrayResponse, response.body)
    }

    @Test
    fun `test real json array serialization with api taking single parameters`() {
        val response = client.call(
            POST,
            WebRequest<Any>(
                "objects-in-json-endpoint/create-with-individual-params",
                realJsonArrayPayload
            ),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(expectedArrayResponse, response.body)
    }
}
