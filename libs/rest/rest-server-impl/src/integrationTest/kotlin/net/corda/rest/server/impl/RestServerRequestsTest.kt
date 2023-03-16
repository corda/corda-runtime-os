package net.corda.rest.server.impl

import com.google.gson.Gson
import io.javalin.core.util.Header.ACCESS_CONTROL_ALLOW_CREDENTIALS
import io.javalin.core.util.Header.ACCESS_CONTROL_ALLOW_ORIGIN
import io.javalin.core.util.Header.CACHE_CONTROL
import io.javalin.core.util.Header.WWW_AUTHENTICATE
import net.corda.rest.server.apigen.test.TestJavaPrimitivesRestResourceImpl
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.server.impl.apigen.processing.openapi.schema.toExample
import net.corda.rest.test.*
import net.corda.rest.tools.HttpVerb.DELETE
import net.corda.rest.tools.HttpVerb.GET
import net.corda.rest.tools.HttpVerb.POST
import net.corda.rest.tools.HttpVerb.PUT
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import net.corda.rest.test.utils.ChecksumUtil
import net.corda.rest.test.utils.TestClientFileUpload
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.WebRequest
import net.corda.rest.test.utils.WebResponse
import net.corda.rest.test.utils.multipartDir

class RestServerRequestsTest : RestServerTestBase() {
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
                    TestHealthCheckAPIImpl(),
                    TestJavaPrimitivesRestResourceImpl(),
                    CustomSerializationAPIImpl(),
                    TestEntityRestResourceImpl(),
                    TestFileUploadImpl()
                ),
                ::securityManager,
                restServerSettings,
                multipartDir,
                true
            ).apply { start() }
            client = TestHttpClientUnirestImpl("http://${restServerSettings.address.host}:${server.port}/" +
                    "${restServerSettings.context.basePath}/v${restServerSettings.context.version}/")
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
        assertEquals("Sane", sanityResponse.body)
    }

    @Test
    fun `POST ping returns Pong with custom deserializer`() {

        fun  WebResponse<String>.doAssert() {
            assertEquals(HttpStatus.SC_OK, responseStatus)
            assertEquals("application/json", headers["Content-Type"])
            assertEquals("Pong for str = stringdata", body)
        }

        // Call with explicit "pingPongData" in the root JSON
        client.call(POST, WebRequest("health/ping", """{"pingPongData": {"str": "stringdata"}}"""), userName, password)
            .doAssert()

        // Call without explicit "pingPongData" in the root JSON
        client.call(POST, WebRequest("health/ping", """{"str": "stringdata"}"""), userName, password)
            .doAssert()
    }

    //https://r3-cev.atlassian.net/browse/CORE-2491
    @Test
    fun `POST empty body doesn't throw exception if all parameters are optional`() {

        val pingResponse = client.call(POST, WebRequest("health/ping", ""), userName, password)
        assertEquals(HttpStatus.SC_OK, pingResponse.responseStatus)
        assertEquals("Pong for null", pingResponse.body)
    }

    @Test
    fun `GET void returns NO_CONTENT and no body`() {
        val pingResponse = client.call(GET, WebRequest<Any>("health/void"), userName, password)
        assertEquals(HttpStatus.SC_NO_CONTENT, pingResponse.responseStatus)
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
        assertEquals("Hello 1 : world", helloResponse.body)
    }

    @Test
    fun `GET hello name returns string without optional query param`() {

        val helloResponse = client.call(GET, WebRequest<Any>("health/hello/world"), userName, password)
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals("Hello null : world", helloResponse.body)
    }

    @Test
    fun `GET hello2 name returns string greeting name`() {

        val fullUrl = "health/hello2/pathString?id=id"
        val helloResponse = client.call(GET, WebRequest<Any>(fullUrl), userName, password)
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals("Hello queryParam: id, pathParam : pathString", helloResponse.body)

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

        val fullUrlWithSlashes = "testentity/1234/"
        val fullUrlWithoutSlash = "testentity/1234"
        val helloResponse = client.call(GET, WebRequest<Any>(fullUrlWithSlashes), userName, password)
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals("Retrieved using id: 1234", helloResponse.body)

        // Check full URL received by the Security Manager
        assertThat(securityManager.checksExecuted.map { it.action }).hasSize(1)
            .allMatch { it == "GET:/api/v1/$fullUrlWithoutSlash" }
    }

    @Test
    fun `missing not required query parameter should not throw`() {
        val helloResponse = client.call(GET, WebRequest<Any>("health/hello2/pathString"), userName, password)
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals("Hello queryParam: null, pathParam : pathString", helloResponse.body)
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
        assertEquals("text", reverseTextResponse.body)
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
        assertEquals("a b", reverseTextResponse.body)
    }

    @Test
    fun `POST body playground should not fail when values are passed as null`() {
        val reverseTextResponse = client.call(POST, WebRequest<Any>("health/bodyplayground", """ { "s1": null, "s2": null } """), userName, password)
        assertEquals(HttpStatus.SC_OK, reverseTextResponse.responseStatus)
        assertEquals("null null", reverseTextResponse.body)
    }

    @Test
    fun `POST body playground should not fail when optional values are not passed`() {
        val reverseTextResponse = client.call(POST, WebRequest<Any>("health/bodyplayground", """ { "s1": null } """), userName, password)
        assertEquals(HttpStatus.SC_OK, reverseTextResponse.responseStatus)
        assertEquals("null null", reverseTextResponse.body)
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
        assertEquals("2020-01-01T11:00Z[UTC]", timeCallResponse.body)
    }

    @Test
    fun `Provided ZonedDateTime example can be parsed`() {
        val timeCallResponse = client.call(POST, WebRequest<Any>("health/timecall", """ { "time": { "time": "${ZonedDateTime::class.java.toExample()}" } } """), userName, password)
        assertEquals(HttpStatus.SC_OK, timeCallResponse.responseStatus)
    }

    @Test
    fun `POST dateCall should return embedded date value`() {
        val date = "2021-07-29T13:13:14"

        fun WebResponse<String>.doAssert() {
            assertEquals(HttpStatus.SC_OK, responseStatus)
            assertThat(body!!).contains(date)
        }

        // Explicit `date` at the root JSON
        client.call(
            POST,
            WebRequest<Any>("health/datecall", """ { "date": { "date": "$date" } } """),
            userName,
            password
        ).doAssert()

        // Without explicit `date` at the root JSON
        // We are hitting limitation here where inner "date" along with outer "date" confuse our parameter retrieval logic
        // Probably worth providing some guidance to avoid this sort of data structures with clashing attributes
        /*client.call(
            POST,
            WebRequest<Any>("health/datecall", """{ "date": "$date" }"""),
            userName,
            password
        ).doAssert()
         */
    }

    @Test
    fun `POST instantCall should parse and return correct Instant value`() {
        val instant = Instant.parse("2021-04-13T11:44:17.995711Z").toString()

        val instantCallResponse = client.call(POST, WebRequest<Any>("health/instantcall", """ { "instant": { "instant": "$instant" } } """), userName, password)

        assertEquals(HttpStatus.SC_OK, instantCallResponse.responseStatus)
        assertEquals(instant, instantCallResponse.body)
    }

    @Test
    fun `POST with custom marshalling should process the objects the custom way`() {
        val timeCallResponse = client.call(POST, WebRequest<Any>("customjson/printcustommarshal", """ { "s": "text" } """), userName, password)

        assertEquals(HttpStatus.SC_OK, timeCallResponse.responseStatus)
        assertEquals("{\"data\":\"custom text\"}", timeCallResponse.body)
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
        assertEquals("Created using: CreationParams(name=TestName, amount=20)", createEntityResponse.body)
    }

    @Test
    fun `Call get using path on test entity`() {
        val createEntityResponse = client.call(GET, WebRequest<Any>("testentity/myId"), userName, password)

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals("Retrieved using id: myId", createEntityResponse.body)
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
        assertEquals("Retrieved using query: MyQuery", createEntityResponse.body)
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
            "Updated using params: UpdateParams(id=myId, name=TestName, amount=20)",
            createEntityResponse.body
        )
    }

    @Test
    fun `Call delete using path on test entity`() {
        val createEntityResponse = client.call(DELETE, WebRequest<Any>("testentity/myId"), userName, password)

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals("Deleted using id: myId", createEntityResponse.body)
    }

    @Test
    fun `Call delete using query on test entity`() {
        val createEntityResponse = client.call(
            DELETE,
            WebRequest<Any>("testentity", queryParameters = mapOf("query" to "MyQuery")),
            userName,
            password
        )

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals("Deleted using query: MyQuery", createEntityResponse.body)
    }

    @Test
    fun `test generate checksum function`() {
        val inputStream1 = "test text".byteInputStream()
        val inputStream2 = "test text".byteInputStream()

        val checksum1 = ChecksumUtil.generateChecksum(inputStream1)
        val checksum2 = ChecksumUtil.generateChecksum(inputStream2)

        assertEquals(checksum1, checksum2)
    }

    @Test
    fun `file upload using multi-part form request`() {
        val text = "test text"
        val createEntityResponse = client.call(
            POST,
            WebRequest<Any>(
                path = "fileupload/upload",
                files = mapOf(
                    "file" to listOf(TestClientFileUpload(text.byteInputStream(), "uploadedTestFile.txt"))
                )
            ),
            userName,
            password
        )

        val expectedChecksum = ChecksumUtil.generateChecksum(text.byteInputStream())

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals(expectedChecksum, createEntityResponse.body)
    }

    @Test
    fun `file upload with name parameter using multi-part form request`() {
        val text = "test text"
        val createEntityResponse = client.call(
            POST,
            WebRequest<Any>(
                path = "fileupload/uploadwithname",
                formParameters = mapOf("name" to "some-text-as-parameter"),
                files = mapOf(
                    "file" to listOf(TestClientFileUpload(text.byteInputStream(), "uploadedTestFile.txt"))
                )
            ),
            userName,
            password
        )

        val expectedResult = "some-text-as-parameter, ${ChecksumUtil.generateChecksum(text.byteInputStream())}"

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals(expectedResult, createEntityResponse.body)
    }

    @Test
    fun `file upload on API declaring HttpFileUpload object as parameter using multi-part form request`() {
        val text = "test text"
        val createEntityResponse = client.call(
            POST,
            WebRequest<Any>(
                path = "fileupload/fileuploadobject",
                files = mapOf(
                    "file" to listOf(TestClientFileUpload(text.byteInputStream(), "uploadedTestFile.txt"))
                )
            ),
            userName,
            password
        )

        val expectedResult = ChecksumUtil.generateChecksum(text.byteInputStream())

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals(expectedResult, createEntityResponse.body)
    }

    @Test
    fun `file upload on API declaring multiple HttpFileUpload objects as parameters using multi-part form request`() {
        val text1 = "test text 1"
        val text2 = "test text 2"
        val createEntityResponse = client.call(
            POST,
            WebRequest<Any>(
                path = "fileupload/multifileuploadobject",
                files = mapOf(
                    "file1" to listOf(TestClientFileUpload(text1.byteInputStream(), "uploadedTestFile1.txt")),
                    "file2" to listOf(TestClientFileUpload(text2.byteInputStream(), "uploadedTestFile2.txt"))
                )
            ),
            userName,
            password
        )

        val expectedResult = ChecksumUtil.generateChecksum(text1.byteInputStream()) + ", " + ChecksumUtil.generateChecksum(text2.byteInputStream())

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals(expectedResult, createEntityResponse.body)
    }

    @Test
    fun `file upload of list of HttpFileUpload using multi-part form request`() {
        val text1 = "test text 1"
        val text2 = "test text 2"
        val createEntityResponse = client.call(
            POST,
            WebRequest<Any>(
                path = "fileupload/fileuploadobjectlist",
                files = mapOf(
                    "files" to listOf(
                        TestClientFileUpload(text1.byteInputStream(), "uploadedTestFile1.txt"),
                        TestClientFileUpload(text2.byteInputStream(), "uploadedTestFile2.txt")
                    )
                )
            ),
            userName,
            password
        )

        val expectedResult = ChecksumUtil.generateChecksum(text1.byteInputStream()) + ", " + ChecksumUtil.generateChecksum(text2.byteInputStream())

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals(expectedResult, createEntityResponse.body)
    }

    @Test
    fun `file upload of HttpFileUpload using name in annotation`() {
        val text1 = "test text 1"
        val createEntityResponse = client.call(
            POST,
            WebRequest<Any>(
                path = "fileupload/uploadwithnameinannotation",
                files = mapOf(
                    "differentName" to listOf(TestClientFileUpload(text1.byteInputStream(), "uploadedTestFile1.txt"))
                )
            ),
            userName,
            password
        )

        val expectedResult = ChecksumUtil.generateChecksum(text1.byteInputStream())

        assertEquals(HttpStatus.SC_OK, createEntityResponse.responseStatus)
        assertEquals(expectedResult, createEntityResponse.body)
    }

    @Test
    fun `file upload with file missing`() {
        val createEntityResponse = client.call(
            POST,
            WebRequest<Any>(path = "fileupload/upload", formParameters = mapOf("foo" to "bar")),
            userName,
            password
        )

        assertEquals(HttpStatus.SC_BAD_REQUEST, createEntityResponse.responseStatus)
    }

    @Test
    fun `POST call using name in annotation`() {

        val fullUrl = "health/stringmethodwithnameinannotation"
        val helloResponse = client.call(
            POST, WebRequest<Any>(
                fullUrl,
                """{"correctName": "foo"}"""
            ),
            userName, password
        )
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals("Completed foo", helloResponse.body)
    }

    @Test
    fun `test api that returns null object `() {

        val fullUrl = "health/apireturningnullobject"
        val helloResponse = client.call(
            POST, WebRequest<Any>(
                fullUrl
            ),
            userName, password
        )
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals("""null""", helloResponse.body)
    }

    @Test
    fun `test api that returns null string`() {

        val fullUrl = "health/apireturningnullstring"
        val helloResponse = client.call(
            POST, WebRequest<Any>(
                fullUrl
            ),
            userName, password
        )
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals("""null""", helloResponse.body)
    }

    @Test
    fun `test api that returns object wrapping a null string`() {

        val fullUrl = "health/apireturningobjectwithnullablestringinside"
        val helloResponse = client.call(
            POST, WebRequest<Any>(
                fullUrl
            ),
            userName, password
        )
        assertEquals(HttpStatus.SC_OK, helloResponse.responseStatus)
        assertEquals("""{"str":null}""", helloResponse.body)
    }
}
