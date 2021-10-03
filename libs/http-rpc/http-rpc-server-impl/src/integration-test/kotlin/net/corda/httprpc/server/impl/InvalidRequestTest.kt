package net.corda.httprpc.server.impl

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.rpcops.impl.TestHealthCheckControllerImpl
import net.corda.httprpc.server.impl.rpcops.impl.TestJavaPrimitivesControllerImpl
import net.corda.httprpc.server.impl.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.server.impl.utils.WebRequest
import net.corda.httprpc.tools.HttpVerb
import net.corda.v5.base.util.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InvalidRequestTest : HttpRpcServerTestBase() {
    companion object {
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
                listOf(TestHealthCheckControllerImpl(), TestJavaPrimitivesControllerImpl()),
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

    // Existing RPC code handled this scenario differently to Javalin.
    // Turned on [STRICT_DUPLICATE_DETECTION] in [HttpRPCServerSerialization] to provide the same duplication checking.
    @Test
    fun `POST ping with duplicate json key returns 400 BAD REQUEST`() {

        val pingResponse = client.call(
            HttpVerb.POST,
            WebRequest("health/ping", """{"str": "stringdata","str": "duplicate"}"""),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_BAD_REQUEST, pingResponse.responseStatus)
        assertNotNull(pingResponse.body)
        assertEquals("application/json", pingResponse.headers["Content-Type"])
//        assertTrue(pingResponse.body.contains(SERIALIZATION_ERROR))
        assertTrue(pingResponse.body.contains("Couldn't deserialize body to PingPongData"))
    }

    @Test
    fun `POST plusdouble returns returns 400 BAD REQUEST`() {

        val plusDoubleResponse = client.call(HttpVerb.POST, WebRequest<Any>("health/plusdouble", """{"number": 1,0}"""), userName, password)
        assertEquals(HttpStatus.SC_BAD_REQUEST, plusDoubleResponse.responseStatus)
        assertNotNull(plusDoubleResponse.body)
        assertTrue(plusDoubleResponse.body.contains(SERIALIZATION_ERROR))
    }

    // The number checking shown in this test is not handled by Javalin and would require custom code
    // It is beneficial to have this sort of error message returned, at the same time it is a failure scenario, that
    // hopefully wouldn't be hit very often. We could implement similiar behaviour but will require custom code and have
    // people remember to call it when converting a parameter to an Integer or whatever the type may be.
    // I have altered the controller code to return the message that the test is asserting.
    @Test
    fun `POST negateinteger over max size should return 400 BAD REQUEST`() {

        val negateIntResponse =
            client.call(HttpVerb.POST, WebRequest("java/negateinteger", """{"number": 3147483647}"""), userName, password)
        assertEquals(HttpStatus.SC_BAD_REQUEST, negateIntResponse.responseStatus)
        assertNotNull(negateIntResponse.body)
        assertTrue(negateIntResponse.body.contains("Numeric value (3147483647) out of range of int (-2147483648 - 2147483647)"))
    }

    // Existing RPC code handled this scenario differently to Javalin. For now, altered the test and the desired
    // behaviour will need to be decided.
    @Test
    fun `POST ping null value for non-nullable String should return 400 BAD REQUEST`() {

        val pingResponse = client.call(HttpVerb.POST, WebRequest("health/ping", """{"str": null}"""), userName, password)
        assertEquals(HttpStatus.SC_BAD_REQUEST, pingResponse.responseStatus)
        assertNotNull(pingResponse.body)
//        assertTrue(pingResponse.body.contains(MISSING_VALUE_ERROR))
        assertTrue(pingResponse.body.contains("Couldn't deserialize body to PingPongData"))
    }

    // Existing RPC code handled this scenario differently to Javalin. For now, altered the test and the desired
    // behaviour will need to be decided.
    @Test
    fun `POST ping missing value for non-nullable String should return 400 BAD REQUEST`() {

        val pingResponse = client.call(HttpVerb.POST, WebRequest("health/ping", "{}"), userName, password)
        assertEquals(HttpStatus.SC_BAD_REQUEST, pingResponse.responseStatus)
        assertNotNull(pingResponse.body)
//        assertTrue(pingResponse.body.contains(MISSING_VALUE_ERROR))
        assertTrue(pingResponse.body.contains("Couldn't deserialize body to PingPongData"))
    }

    // We should not be using Date
    @Disabled
    @Test
    fun `Timezone specified in date should return 400 BAD REQUEST`() {

        val dateCallResponse = client.call(
            HttpVerb.POST,
            WebRequest<Any>("health/datecall", """ { "date": { "date": "2020-04-13T00:00:00.000+08:00[UTC]" } } """),
            userName,
            password
        )
        assertEquals(HttpStatus.SC_BAD_REQUEST, dateCallResponse.responseStatus)
        assertNotNull(dateCallResponse.body)
        assertTrue(dateCallResponse.body.contains(DATE_PARSE_ERROR))
    }

    // We should not be using Date
    @Disabled
    @Test
    fun `Wrong date format should return 400 BAD REQUEST`() {

        val dateCallResponse = client.call(
            HttpVerb.POST,
            WebRequest<Any>("health/datecall", """ { "date": { "date": "2020-04-13 00:00:00.000+08:00" } } """),
            userName,
            password
        )

        assertEquals(HttpStatus.SC_BAD_REQUEST, dateCallResponse.responseStatus)
        assertNotNull(dateCallResponse.body)
        assertTrue(dateCallResponse.body.contains(DATE_PARSE_ERROR))

        //CORE-2404 case #1 exception contains line break, this is invalid in a json string
        val json = JsonParser.parseString(dateCallResponse.body) as JsonObject
        val responseTitle = json["title"].asString
        assertThat(responseTitle).doesNotContain("\n")
    }

    // Can't have slashes in path params in Javalin v3. There does seem to be support for it in v4 though.
    // That being said, do we really need to support slashes for UUIDs?
    @Disabled
    @Test
    fun `passing 3 backslashes as UUID should be handled properly`() {

        val parseUuidResponse = client.call(HttpVerb.POST, WebRequest<String>("health/parseuuid/%5C%5C%5C"), userName, password)
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, parseUuidResponse.responseStatus)
        assertNotNull(parseUuidResponse.body)
        assertDoesNotThrow(parseUuidResponse.body) { JsonParser.parseString(parseUuidResponse.body) }
    }
}
