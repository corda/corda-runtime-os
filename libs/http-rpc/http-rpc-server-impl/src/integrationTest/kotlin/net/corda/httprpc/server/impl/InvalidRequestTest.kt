package net.corda.httprpc.server.impl

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.corda.httprpc.server.apigen.test.TestJavaPrimitivesRPCopsImpl
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.WebRequest
import net.corda.httprpc.test.utils.WebResponse
import net.corda.httprpc.test.utils.multipartDir
import net.corda.httprpc.tools.HttpVerb
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InvalidRequestTest : HttpRpcServerTestBase() {
    companion object {
        private const val WRONG_PARAMETER = "Unable to parse parameter"
        private const val MISSING_VALUE_ERROR = "value failed for JSON property str due to missing (therefore NULL) value"

        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            val httpRpcSettings = HttpRpcSettings(
                NetworkHostAndPort("localhost", 0),
                context,
                null,
                null,
                HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
                20000L
            )
            server = HttpRpcServerImpl(
                listOf(TestHealthCheckAPIImpl(), TestJavaPrimitivesRPCopsImpl()),
                ::securityManager,
                httpRpcSettings,
                multipartDir,
                true
            ).apply { start() }
            client =
                TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${server.port}/" +
                        "${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            server.close()
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
        assertThat(pingResponse.body).doesNotContain("\"type\":")
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
        assertThat(plusDoubleResponse.body).doesNotContain("\"type\":")
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
        assertFalse(responseBody.contains("\"type\":"))
    }

    @Test
    fun `POST ping null value for non-nullable String should return 400 BAD REQUEST`() {

        fun WebResponse<String>.doAssert() {
            assertEquals(HttpStatus.SC_BAD_REQUEST, responseStatus)
            val responseBody = body
            assertNotNull(responseBody)
            assertThat(responseBody).contains(MISSING_VALUE_ERROR)
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
            assertThat(responseBody).contains(MISSING_VALUE_ERROR)
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
            assertThat(responseBody).contains(WRONG_PARAMETER)
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
            assertThat(responseBody).contains(WRONG_PARAMETER)

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

    @Test
    fun `pass integer in query that cannot be parsed`() {
        fun WebResponse<String>.doAssert() {
            assertEquals(HttpStatus.SC_BAD_REQUEST, responseStatus)
            val responseBody = body
            assertNotNull(responseBody)

            val json = JsonParser.parseString(responseBody) as JsonObject
            val responseTitle = json["title"].asString
            assertThat(responseTitle).contains("Unable to parse parameter 'id'")
        }

        client.call(HttpVerb.GET, WebRequest<Any>("health/hello/world?id=wrongInt"), userName, password).doAssert()
    }

    @Test
    fun `pass integer in path that cannot be parsed`() {
        fun WebResponse<String>.doAssert() {
            assertEquals(HttpStatus.SC_BAD_REQUEST, responseStatus)
            val responseBody = body
            assertNotNull(responseBody)

            val json = JsonParser.parseString(responseBody) as JsonObject
            val responseTitle = json["title"].asString
            assertThat(responseTitle).contains("Unable to parse parameter 'number'")
        }

        client.call(HttpVerb.POST, WebRequest<Any>("health/plusone/wrongInt"), userName, password).doAssert()
    }
}
