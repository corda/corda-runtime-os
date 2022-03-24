package net.corda.httprpc.server.impl

import com.google.gson.Gson
import io.javalin.core.util.Header.ACCESS_CONTROL_ALLOW_CREDENTIALS
import io.javalin.core.util.Header.ACCESS_CONTROL_ALLOW_ORIGIN
import io.javalin.core.util.Header.CACHE_CONTROL
import io.javalin.core.util.Header.WWW_AUTHENTICATE
import net.corda.httprpc.server.apigen.test.TestJavaPrimitivesRPCopsImpl
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.toExample
import net.corda.httprpc.server.impl.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.server.impl.utils.WebRequest
import net.corda.httprpc.server.impl.utils.multipartDir
import net.corda.httprpc.test.*
import net.corda.httprpc.tools.HttpVerb.GET
import net.corda.httprpc.tools.HttpVerb.POST
import net.corda.httprpc.tools.HttpVerb.PUT
import net.corda.v5.application.flows.BadRpcStartFlowRequestException
import net.corda.v5.base.util.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.test.assertEquals


class HttpRpcServerRequestsTest : HttpRpcServerTestBase() {
    companion object {
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
                listOf(
                    TestHealthCheckAPIImpl(),
                    TestJavaPrimitivesRPCopsImpl(),
                    CustomSerializationAPIImpl(),
                    TestEntityRpcOpsImpl()
                ),
                securityManager,
                httpRpcSettings,
                multipartDir,
                true
            ).apply { start() }
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

    @AfterEach
    fun reset() {
        CustomUnsafeString.flag = false
        CustomNonSerializableString.flag = false
        securityManager.forgetChecks()
    }

    @Test
    fun `get invalid path returns 404 NOT FOUND`() {

        val invalidPathResponse = client.call(GET, WebRequest<Any>("invalidPath"), userName, password)
        assertEquals(HttpStatus.SC_NOT_FOUND, invalidPathResponse.responseStatus)
    }

    @Test
    fun `valid path returns 200 OK`() {

        val getPathResponse = client.call(GET, WebRequest<Any>("health/sanity"), userName, password)
        assertEquals(HttpStatus.SC_OK, getPathResponse.responseStatus)
        assertEquals("localhost", getPathResponse.headers[ACCESS_CONTROL_ALLOW_ORIGIN])
        assertEquals("true", getPathResponse.headers[ACCESS_CONTROL_ALLOW_CREDENTIALS])
        assertEquals("no-cache", getPathResponse.headers[CACHE_CONTROL])
    }

    @Test
    fun `GET sanity returns Sane value`() {
        val sanityResponse = client.call(GET, WebRequest<Any>("health/sanity"), userName, password)
        assertEquals(HttpStatus.SC_OK, sanityResponse.responseStatus)
        assertEquals(""""Sane"""", sanityResponse.body)
    }

    @Test
    fun `POST ping returns Pong with custom deserializer`() {

        val pingResponse = client.call(POST, WebRequest("health/ping", """{"pingPongData": {"str": "stringdata"}}"""), userName, password)
        assertEquals(HttpStatus.SC_OK, pingResponse.responseStatus)
        assertEquals("application/json", pingResponse.headers["Content-Type"])
        assertEquals(""""Pong for str = stringdata"""", pingResponse.body)
    }

    //https://r3-cev.atlassian.net/browse/CORE-2491
    @Test
    fun `POST empty body doesn't throw exception if all parameters are optional`() {

        val pingResponse = client.call(POST, WebRequest("health/ping", ""), userName, password)
        assertEquals(HttpStatus.SC_OK, pingResponse.responseStatus)
        assertEquals(""""Pong for null"""", pingResponse.body)
    }

    @Test
    fun `GET void returns nothing`() {
        val pingResponse = client.call(GET, WebRequest<Any>("health/void"), userName, password)
        assertEquals(HttpStatus.SC_OK, pingResponse.responseStatus)
        assertEquals("", pingResponse.body)
    }

    @Test
    fun `GET plusone with list returns list with incremented elements`() {

        val plusOneResponse = client.call(GET, WebRequest<Any>("health/plusone", queryParameters = mapOf("numbers" to listOf(1.0, 2.0))), List::class.java, userName, password)
        assertEquals(HttpStatus.SC_OK, plusOneResponse.responseStatus)
        assertEquals("application/json", plusOneResponse.headers["Content-Type"])
        assertEquals(listOf(2.0, 3.0), plusOneResponse.body)
    }

    @Test
    fun `GET plusone with not required list parameter not provided returns list`() {
        val plusOneResponse = client.call(GET, WebRequest<Any>("health/plusone"), List::class.java, userName, password)
        assertEquals(HttpStatus.SC_OK, plusOneResponse.responseStatus)
        assertEquals(emptyList<Double>(), plusOneResponse.body)
    }

    @Test
    fun `GET plusone with not required parameter provided returns list with incremented elements`() {
        val plusOneResponse = client.call(GET, WebRequest<Any>("health/plusone", queryParameters = mapOf("numbers" to listOf(1.0, 2.0))), List::class.java, userName, password)
        assertEquals(HttpStatus.SC_OK, plusOneResponse.responseStatus)
        assertEquals(listOf(2.0, 3.0), plusOneResponse.body)
    }

    @Test
    fun `GET hello name returns string greeting name`() {

        val helloResponse = client.call(GET, WebRequest<Any>("health/hello/world?id=1"), userName, password)
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals(""""Hello 1 : world"""", helloResponse.body)
    }

    @Test
    fun `GET hello2 name returns string greeting name`() {

        val fullUrl = "health/hello2/pathString?id=id"
        val helloResponse = client.call(GET, WebRequest<Any>(fullUrl), userName, password)
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals(""""Hello queryParam: id, pathParam : pathString"""", helloResponse.body)

        // Check full URL received by the Security Manager
        assertThat(securityManager.checksExecuted.map { it.action }).hasSize(1).allMatch { it.contains(fullUrl) }
    }

    @Test
    fun `Verify no permission check on GetProtocolVersion`() {

        val fullUrl = "testEntity/getProtocolVersion"
        val helloResponse = client.call(GET, WebRequest<Any>(fullUrl), userName, password)
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals("3", helloResponse.body)

        // Check that security managed has not been called for GetProtocolVersion which is exempt from permissions check
        assertThat(securityManager.checksExecuted).hasSize(0)
    }

    @Test
    fun `Verify permission check is performed on entity retrieval`() {

        val fullUrl = "testentity/1234"
        val helloResponse = client.call(GET, WebRequest<Any>(fullUrl), userName, password)
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals("\"Retrieved using id: 1234\"", helloResponse.body)

        // Check full URL received by the Security Manager
        assertThat(securityManager.checksExecuted.map { it.action }).hasSize(1).allMatch { it.contains(fullUrl) }
    }

    @Test
    fun `missing not required query parameter should not throw`() {
        val helloResponse = client.call(GET, WebRequest<Any>("health/hello2/pathString"), userName, password)
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals(""""Hello queryParam: null, pathParam : pathString"""", helloResponse.body)
    }

    @Test
    fun `POST plusone returns increased number`() {

        val reverseTextResponse = client.call(POST, WebRequest<Any>("health/plusone/2999999999"), userName, password)
        assertEquals(HttpStatus.SC_OK, reverseTextResponse.responseStatus)
        assertEquals("application/json", reverseTextResponse.headers["Content-Type"])
        assertEquals("3000000000", reverseTextResponse.body)
    }

    @Test
    fun `POST negateinteger should return the negated number`() {

        val negateIntResponse = client.call(POST, WebRequest("java/negateinteger", """{"number": 1}"""), userName, password)
        assertEquals(HttpStatus.SC_OK, negateIntResponse.responseStatus)
        assertEquals("application/json", negateIntResponse.headers["Content-Type"])
        assertEquals("-1", negateIntResponse.body)
    }

    @Test
    fun `POST negateprimitiveinteger should return the negated number`() {

        val negateIntResponse = client.call(POST, WebRequest("java/negateprimitiveinteger", """{"number": 1}"""), userName, password)
        assertEquals(HttpStatus.SC_OK, negateIntResponse.responseStatus)
        assertEquals("application/json", negateIntResponse.headers["Content-Type"])
        assertEquals("-1", negateIntResponse.body)
    }


    @Test
    fun `GET negate_long should return the negated number`() {

        val negateLongResponse = client.call(GET, WebRequest<Any>("java/negate_long?number=3000000000"), userName, password)
        assertEquals(HttpStatus.SC_OK, negateLongResponse.responseStatus)
        assertEquals("-3000000000", negateLongResponse.body)
    }

    @Test
    fun `GET negate_boolean should return the negated boolean`() {

        val negateBooleanResponse = client.call(GET, WebRequest<Any>("java/negate_boolean?bool=true"), userName, password)
        assertEquals(HttpStatus.SC_OK, negateBooleanResponse.responseStatus)
        assertEquals("false", negateBooleanResponse.body)
    }

    @Test
    fun `GET reverse text should return the reversed text`() {

        val reverseTextResponse = client.call(GET, WebRequest<Any>("java/reverse/txet"), userName, password)
        assertEquals(HttpStatus.SC_OK, reverseTextResponse.responseStatus)
        assertEquals(""""text"""", reverseTextResponse.body)
    }

    private fun String.asMapFromJson() = Gson().fromJson(this, Map::class.java)

    @Test
    fun `GET without auth header returns HTTP 401 Unauthorized`() {
        val getPathResponse = client.call(GET, WebRequest<String>("health/sanity"))
        assertEquals(HttpStatus.SC_UNAUTHORIZED, getPathResponse.responseStatus)
        assertEquals("User credentials are empty or cannot be resolved", getPathResponse.body!!.asMapFromJson()["title"])
    }

    @Test
    fun `GET without auth header returns WWW-Authenticate header`() {
        val getPathResponse = client.call(GET, WebRequest<Any>("health/sanity"))
        val headerValue = getPathResponse.headers[WWW_AUTHENTICATE]
        assertEquals("Basic realm=\"FakeSecurityManager\"", headerValue)
    }

    @Test
    fun `GET invalid user returns HTTP 401 Unauthorized`() {
        val getPathResponse = client.call(GET, WebRequest<Any>("health/sanity"), "invalidUser", password)
        assertEquals(HttpStatus.SC_UNAUTHORIZED, getPathResponse.responseStatus)
        assertEquals("Error during user authentication", getPathResponse.body!!.asMapFromJson()["title"])
    }

    @Test
    fun `GET invalid password returns HTTP 401 Unauthorized`() {
        val getPathResponse = client.call(GET, WebRequest<Any>("health/sanity"), userName, "invalidPassword")
        assertEquals(HttpStatus.SC_UNAUTHORIZED, getPathResponse.responseStatus)
        assertEquals("Error during user authentication", getPathResponse.body!!.asMapFromJson()["title"])
    }

    @Test
    fun `GET valid user with permissions on requested method with different case returns 200`() {
        val getPathResponse = client.call(GET, WebRequest<Any>("health/sanity"), userName, password)
        assertEquals(HttpStatus.SC_OK, getPathResponse.responseStatus)
    }

    @Test
    fun `GET valid user with valid permissions on requested get protocol version returns 200`() {
        val getPathResponse = client.call(GET, WebRequest<Any>("health/getprotocolversion"), userName, password)
        assertEquals(HttpStatus.SC_OK, getPathResponse.responseStatus)
        assertEquals("2", getPathResponse.body)
    }

    @Test
    fun `GET invalid user without permissions on requested get protocol version returns 200`() {
        val getPathResponse = client.call(GET, WebRequest<Any>("health/getprotocolversion"), "invalid", "invalid")
        assertEquals(HttpStatus.SC_OK, getPathResponse.responseStatus)
        assertEquals("2", getPathResponse.body)
    }

    @Test
    fun `POST body playground should not fail when values are passed`() {
        val reverseTextResponse = client.call(POST, WebRequest<Any>("health/bodyplayground", """ { "s1": "a", "s2": "b" } """), userName, password)
        assertEquals(HttpStatus.SC_OK, reverseTextResponse.responseStatus)
        assertEquals(""""a b"""", reverseTextResponse.body)
    }

    @Test
    fun `POST body playground should not fail when values are passed as null`() {
        val reverseTextResponse = client.call(POST, WebRequest<Any>("health/bodyplayground", """ { "s1": null, "s2": null } """), userName, password)
        assertEquals(HttpStatus.SC_OK, reverseTextResponse.responseStatus)
        assertEquals(""""null null"""", reverseTextResponse.body)
    }

    @Test
    fun `POST body playground should not fail when optional values are not passed`() {
        val reverseTextResponse = client.call(POST, WebRequest<Any>("health/bodyplayground", """ { "s1": null } """), userName, password)
        assertEquals(HttpStatus.SC_OK, reverseTextResponse.responseStatus)
        assertEquals(""""null null"""", reverseTextResponse.body)
    }

    @Test
    fun `POST body playground should fail when different case is used`() {
        val reverseTextResponse = client.call(POST, WebRequest<Any>("health/bodyplayground", """ { "S1": null } """), userName, password)
        assertEquals(HttpStatus.SC_BAD_REQUEST, reverseTextResponse.responseStatus)
    }

    @Test
    fun `POST body playground should fail when required values are not passed`() {
        val reverseTextResponse = client.call(POST, WebRequest<Any>("health/bodyplayground", """ { "s2": null } """), userName, password)
        assertEquals(HttpStatus.SC_BAD_REQUEST, reverseTextResponse.responseStatus)
    }

    @Test
    fun `POST timeCall should return correct time zone`() {
        val time = ZonedDateTime.parse("2020-01-01T12:00:00+01:00[Europe/Paris]").toString()

        val timeCallResponse = client.call(POST, WebRequest<Any>("health/timecall", """ { "time": { "time": "$time" } } """), userName, password)

        assertEquals(HttpStatus.SC_OK, timeCallResponse.responseStatus)
        assertEquals(""""2020-01-01T11:00Z[UTC]"""", timeCallResponse.body)
    }

    @Test
    fun `Provided ZonedDateTime example can be parsed`() {
        val timeCallResponse = client.call(POST, WebRequest<Any>("health/timecall", """ { "time": { "time": "${ZonedDateTime::class.java.toExample()}" } } """), userName, password)
        assertEquals(HttpStatus.SC_OK, timeCallResponse.responseStatus)
    }

    @Test
    fun `POST dateCall should return embedded date value`() {
        val date = "2021-07-29T13:13:14"
        val dateCallResponse = client.call(POST, WebRequest<Any>("health/datecall", """ { "date": { "date": "$date" } } """), userName, password)
        assertEquals(HttpStatus.SC_OK, dateCallResponse.responseStatus)
        assertThat(dateCallResponse.body!!).contains("\"2021-07-29T13:13:14\"")
    }

    @Test
    fun `POST instantCall should parse and return correct Instant value`() {
        val instant = Instant.parse("2021-04-13T11:44:17.995711Z").toString()

        val instantCallResponse = client.call(POST, WebRequest<Any>("health/instantcall", """ { "instant": { "instant": "$instant" } } """), userName, password)

        assertEquals(HttpStatus.SC_OK, instantCallResponse.responseStatus)
        assertEquals(""""$instant"""", instantCallResponse.body)
    }

    @Test
    fun `POST with custom marshalling should process the objects the custom way`() {
        val timeCallResponse = client.call(POST, WebRequest<Any>("customjson/printcustommarshal", """ { "s": "text" } """), userName, password)

        assertEquals(HttpStatus.SC_OK, timeCallResponse.responseStatus)
        assertEquals("{\"data\":\"custom text\"}", timeCallResponse.body)
    }

    @Test
    fun `BadRequestException should be converted to BadRequestResponse`() {
        val throwExceptionResponse = client.call(GET, WebRequest<Any>("health/throwexception?exception=${BadRpcStartFlowRequestException::class.java.name}"), userName, password)
        assertEquals(HttpStatus.SC_BAD_REQUEST, throwExceptionResponse.responseStatus)
    }

    @Test
    fun `POST with non cordaSerializable should not run the init of the object`() {
        client.call(POST, WebRequest<Any>("customjson/unsafe", """ { "s": { "data": "value" } }"""), userName, password)
        client.call(POST, WebRequest<Any>("customjson/unsafe", """ { "s": { "unsafe": "value" } }"""), userName, password)

        assert(CustomUnsafeString.flag)
        assert(!CustomNonSerializableString.flag)
    }

    @Test
    fun `Unmapped exception should be converted to HttpResponseException with 500 status`() {

        val throwExceptionResponse = client.call(GET, WebRequest<Any>("health/throwexception?exception=java.lang.IllegalArgumentException"), userName, password)
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, throwExceptionResponse.responseStatus)
    }

    @Test
    fun `Call create on test entity`() {
        val createEntityResponse = client.call(
            POST,
            WebRequest<Any>("testentity", """{ "creationParams" : { "name": "TestName", "amount" : 20 } }"""),
            userName,
            password
        )

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals("\"Created using: CreationParams(name=TestName, amount=20)\"", createEntityResponse.body)
    }

    @Test
    fun `Call get using path on test entity`() {
        val createEntityResponse = client.call(GET, WebRequest<Any>("testentity/myId"), userName, password)

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals("\"Retrieved using id: myId\"", createEntityResponse.body)
    }

    @Test
    fun `Call get using query on test entity`() {
        val createEntityResponse = client.call(
            GET,
            WebRequest<Any>("testentity", queryParameters = mapOf("query" to "MyQuery")),
            userName,
            password
        )

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals("\"Retrieved using query: MyQuery\"", createEntityResponse.body)
    }

    @Test
    fun `Call update on test entity`() {
        val createEntityResponse = client.call(
            PUT,
            WebRequest<Any>(
                "testentity",
                """{ "updateParams" : { "id": "myId", "name": "TestName", "amount" : 20 } }"""
            ),
            userName,
            password
        )

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals(
            "\"Updated using params: UpdateParams(id=myId, name=TestName, amount=20)\"",
            createEntityResponse.body
        )
    }
}
