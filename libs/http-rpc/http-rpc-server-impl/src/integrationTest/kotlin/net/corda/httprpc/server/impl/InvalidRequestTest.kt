package net.corda.httprpc.server.impl

import com.google.gson.JsonObject
import com.google.gson.JsonParser

import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.httprpc.server.apigen.test.TestJavaPrimitivesRPCopsImpl
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.server.impl.utils.WebRequest
import net.corda.httprpc.test.TestHealthCheckAPIImpl
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
        const val SERIALIZATION_ERROR = "Couldn't deserialize body to ObjectNode"
        const val MISSING_VALUE_ERROR = "value failed for JSON property str due to missing (therefore NULL) value"
        const val DATE_PARSE_ERROR = "Cannot deserialize value of type `java.util.Date` from String"

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
                securityManager,
                httpRpcSettings,
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
            net.corda.httprpc.tools.HttpVerb.POST,
            WebRequest("health/ping", """{"data": {"data": "stringdata","data": "duplicate"}}"""),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_BAD_REQUEST, pingResponse.responseStatus)
        assertNotNull(pingResponse.body)
        assertEquals("application/json", pingResponse.headers["Content-Type"])
        assertTrue(pingResponse.body.contains(SERIALIZATION_ERROR))
    }

    @Test
    fun `POST plusdouble returns returns 400 BAD REQUEST`() {

        val plusDoubleResponse = client.call(
            net.corda.httprpc.tools.HttpVerb.POST,
            WebRequest<Any>("health/plusdouble", """{"number": 1,0}"""),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_BAD_REQUEST, plusDoubleResponse.responseStatus)
        assertNotNull(plusDoubleResponse.body)
        assertTrue(plusDoubleResponse.body.contains(SERIALIZATION_ERROR))
    }

    @Test
    fun `POST negateinteger over max size should return 400 BAD REQUEST`() {

        val negateIntResponse = client.call(
            net.corda.httprpc.tools.HttpVerb.POST,
            WebRequest("java/negateinteger", """{"number": 3147483647}"""),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_BAD_REQUEST, negateIntResponse.responseStatus)
        assertNotNull(negateIntResponse.body)
        assertTrue(negateIntResponse.body.contains("Numeric value (3147483647) out of range of int (-2147483648 - 2147483647)"))
    }

    @Test
    fun `POST ping null value for non-nullable String should return 400 BAD REQUEST`() {

        val pingResponse = client.call(
            net.corda.httprpc.tools.HttpVerb.POST,
            WebRequest("health/ping", """{"pingPongData": {"str": null}}"""),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_BAD_REQUEST, pingResponse.responseStatus)
        assertNotNull(pingResponse.body)
        assertTrue(pingResponse.body.contains(MISSING_JSON_FIELD_TITLE))
        assertTrue(pingResponse.body.contains(MISSING_VALUE_ERROR))
    }

    @Test
    fun `POST ping missing value for non-nullable String should return 400 BAD REQUEST`() {

        val pingResponse =
            client.call(net.corda.httprpc.tools.HttpVerb.POST, WebRequest("health/ping", """{"pingPongData": {}}"""), userName, password)
        assertEquals(HttpStatus.SC_BAD_REQUEST, pingResponse.responseStatus)
        assertNotNull(pingResponse.body)
        assertTrue(pingResponse.body.contains(MISSING_JSON_FIELD_TITLE))
        assertTrue(pingResponse.body.contains(MISSING_VALUE_ERROR))
    }

    @Test
    fun `Timezone specified in date should return 400 BAD REQUEST`() {

        val dateCallResponse = client.call(
            net.corda.httprpc.tools.HttpVerb.POST,
            WebRequest<Any>("health/datecall", """ { "date": { "date": "2020-04-13T00:00:00.000+08:00[UTC]" } } """),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_BAD_REQUEST, dateCallResponse.responseStatus)
        assertNotNull(dateCallResponse.body)
        assertTrue(dateCallResponse.body.contains(JSON_PROCESSING_ERROR_TITLE))
        assertTrue(dateCallResponse.body.contains(DATE_PARSE_ERROR))
    }

    @Test
    fun `Wrong date format should return 400 BAD REQUEST`() {

        val dateCallResponse = client.call(
            net.corda.httprpc.tools.HttpVerb.POST,
            WebRequest<Any>("health/datecall", """ { "date": { "date": "2020-04-13 00:00:00.000+08:00" } } """),
            userName,
            password
        )

        assertEquals(HttpStatus.SC_BAD_REQUEST, dateCallResponse.responseStatus)
        assertNotNull(dateCallResponse.body)
        assertTrue(dateCallResponse.body.contains(JSON_PROCESSING_ERROR_TITLE))
        assertTrue(dateCallResponse.body.contains(DATE_PARSE_ERROR))

        //CORE-2404 case #1 exception contains line break, this is invalid in a json string
        val json = JsonParser.parseString(dateCallResponse.body) as JsonObject
        val responseTitle = json["title"].asString
        assertThat(responseTitle).doesNotContain("\n")
    }

    @Test
    fun `passing 3 backslashes as UUID should be handled properly`() {

        val parseUuidResponse =
            client.call(net.corda.httprpc.tools.HttpVerb.POST, WebRequest<String>("health/parseuuid/%5C%5C%5C"), userName, password)
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, parseUuidResponse.responseStatus)
        assertNotNull(parseUuidResponse.body)
        assertDoesNotThrow(parseUuidResponse.body) { JsonParser.parseString(parseUuidResponse.body) }
    }
}
