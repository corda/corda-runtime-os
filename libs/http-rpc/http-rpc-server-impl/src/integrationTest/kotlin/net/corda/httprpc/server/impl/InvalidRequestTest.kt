package net.corda.httprpc.server.impl

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.corda.httprpc.server.apigen.test.TestJavaPrimitivesRPCopsImpl
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.WebRequest
import net.corda.httprpc.test.utils.WebResponse
import net.corda.httprpc.test.utils.findFreePort
import net.corda.httprpc.test.utils.multipartDir
import net.corda.httprpc.tools.HttpVerb
import net.corda.v5.base.util.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InvalidRequestTest : HttpRpcServerTestBase() {
    companion object {
        const val JSON_PROCESSING_ERROR_TITLE = "Error during processing of request JSON."
        const val MISSING_JSON_FIELD_TITLE = "Missing or invalid field in JSON request body."
        const val MISSING_VALUE_ERROR = "value failed for JSON property str due to missing (therefore NULL) value"

        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            val httpRpcSettings = HttpRpcSettings(
                NetworkHostAndPort("localhost", findFreePort()),
                context,
                null,
                null,
                HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE
            )
            server = HttpRpcServerImpl(
                listOf(TestHealthCheckAPIImpl(), TestJavaPrimitivesRPCopsImpl()),
                ::securityManager,
                httpRpcSettings,
                multipartDir,
                true
            ).apply { start() }
            client =
                TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            server.stop()
        }
    }

    @Test
    fun `POST ping with duplicate json key returns 400 BAD REQUEST`() {

        val pingResponse = client.call(
            HttpVerb.POST,
            WebRequest("health/ping", """{"data": {"data": "stringdata","data": "duplicate"}}"""),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_BAD_REQUEST, pingResponse.responseStatus)
        assertNotNull(pingResponse.body)
        assertEquals("application/json", pingResponse.headers["Content-Type"])
        assertThat(pingResponse.body).contains("Duplicate field 'data'")
    }

    @Test
    fun `POST plusdouble returns returns 400 BAD REQUEST`() {

        val plusDoubleResponse = client.call(
            HttpVerb.POST,
            WebRequest<Any>("health/plusdouble", """{"number": 1,0}"""),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_BAD_REQUEST, plusDoubleResponse.responseStatus)
        assertNotNull(plusDoubleResponse.body)
        assertThat(plusDoubleResponse.body).contains("Unexpected character ('0' (code 48))")
    }

    @Test
    fun `POST negateinteger over max size should return 400 BAD REQUEST`() {

        val negateIntResponse = client.call(
            HttpVerb.POST,
            WebRequest("java/negateinteger", """{"number": 3147483647}"""),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_BAD_REQUEST, negateIntResponse.responseStatus)
        val responseBody = negateIntResponse.body
        assertNotNull(responseBody)
        assertTrue(responseBody.contains("Numeric value (3147483647) out of range of int (-2147483648 - 2147483647)"))
    }

    @Test
    fun `POST ping null value for non-nullable String should return 400 BAD REQUEST`() {

        fun WebResponse<String>.doAssert() {
            assertEquals(HttpStatus.SC_BAD_REQUEST, responseStatus)
            val responseBody = body
            assertNotNull(responseBody)
            assertTrue(responseBody.contains(MISSING_JSON_FIELD_TITLE))
            assertTrue(responseBody.contains(MISSING_VALUE_ERROR))
        }

        // With explicit "pingPongData" in the root JSON
        client.call(
            HttpVerb.POST,
            WebRequest("health/ping", """{"pingPongData": {"str": null}}"""),
            userName,
            password
        ).doAssert()

        // Without explicit "pingPongData" in the root JSON
        client.call(
            HttpVerb.POST,
            WebRequest("health/ping", """{"str": null}"""),
            userName,
            password
        ).doAssert()
    }

    @Test
    fun `POST ping missing value for non-nullable String should return 400 BAD REQUEST`() {

        fun WebResponse<String>.doAssert() {
            assertEquals(HttpStatus.SC_BAD_REQUEST, responseStatus)
            val responseBody = body
            assertNotNull(responseBody)
            assertTrue(responseBody.contains(MISSING_JSON_FIELD_TITLE))
            assertTrue(responseBody.contains(MISSING_VALUE_ERROR))
        }

        // With explicit "pingPongData" in the root JSON
        client.call(
            HttpVerb.POST,
            WebRequest("health/ping", """{"pingPongData": {}}"""),
            userName,
            password
        ).doAssert()

        // Without explicit "pingPongData" in the root JSON
        client.call(
            HttpVerb.POST,
            WebRequest("health/ping", """{}"""),
            userName,
            password
        ).doAssert()

    }

    @Test
    fun `Timezone specified in date should return 400 BAD REQUEST`() {

        fun WebResponse<String>.doAssert() {
            assertEquals(HttpStatus.SC_BAD_REQUEST, responseStatus)
            val responseBody = body
            assertNotNull(responseBody)
            assertThat(responseBody).contains(JSON_PROCESSING_ERROR_TITLE)
        }

        // With "date" at the root of JSON
        client.call(
            HttpVerb.POST,
            WebRequest<Any>("health/datecall", """ { "date": { "date": "2020-04-13T00:00:00.000+08:00[UTC]" } } """),
            userName,
            password
        ).doAssert()

        // Without "date" at the root of JSON
        client.call(
            HttpVerb.POST,
            WebRequest<Any>("health/datecall", """{ "date": "2020-04-13T00:00:00.000+08:00[UTC]" }"""),
            userName,
            password
        ).doAssert()
    }

    @Test
    fun `Wrong date format should return 400 BAD REQUEST`() {

        fun WebResponse<String>.doAssert() {
            assertEquals(HttpStatus.SC_BAD_REQUEST, responseStatus)
            val responseBody = body
            assertNotNull(responseBody)
            assertThat(responseBody).contains(JSON_PROCESSING_ERROR_TITLE)

            //CORE-2404 case #1 exception contains line break, this is invalid in a json string
            val json = JsonParser.parseString(responseBody) as JsonObject
            val responseTitle = json["title"].asString
            assertThat(responseTitle).doesNotContain("\n")
        }

        // With "date" at the root of JSON
        client.call(
            HttpVerb.POST,
            WebRequest<Any>("health/datecall", """ { "date": { "date": "2020-04-13 00:00:00.000+08:00" } } """),
            userName,
            password
        ).doAssert()

        // Without "date" at the root of JSON
        client.call(
            HttpVerb.POST,
            WebRequest<Any>("health/datecall", """{ "date": "2020-04-13 00:00:00.000+08:00" }"""),
            userName,
            password
        ).doAssert()
    }

    @Test
    fun `passing 3 backslashes as UUID should be handled properly`() {

        val parseUuidResponse =
            client.call(HttpVerb.POST, WebRequest<String>("health/parseuuid/%5C%5C%5C"), userName, password)
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, parseUuidResponse.responseStatus)
        val responseBody = parseUuidResponse.body
        assertNotNull(responseBody)
        assertDoesNotThrow(responseBody) { JsonParser.parseString(responseBody) }
    }
}
