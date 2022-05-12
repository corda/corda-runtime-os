package net.corda.httprpc.server.impl

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ArraySchema
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.internal.OptionalDependency
import net.corda.httprpc.server.impl.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.server.impl.utils.WebRequest
import net.corda.httprpc.server.impl.utils.compact
import net.corda.httprpc.server.impl.utils.multipartDir
import net.corda.httprpc.test.CalendarRPCOpsImpl
import net.corda.httprpc.test.TestEntityRpcOpsImpl
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.tools.HttpVerb.GET
import net.corda.v5.base.util.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.corda.httprpc.test.TestFileUploadImpl

class HttpRpcServerOpenApiTest : HttpRpcServerTestBase() {
    companion object {
        val httpRpcSettings = HttpRpcSettings(NetworkHostAndPort("localhost", findFreePort()), context, null, null, HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE)
        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            server = HttpRpcServerImpl(
                listOf(CalendarRPCOpsImpl(), TestHealthCheckAPIImpl(), TestEntityRpcOpsImpl(), TestFileUploadImpl()),
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

    @Test
    fun `GET openapi should return the OpenApi spec json`() {

        val apiSpec = client.call(GET, WebRequest<Any>("swagger.json"))
        assertEquals(HttpStatus.SC_OK, apiSpec.responseStatus)
        assertEquals("application/json", apiSpec.headers["Content-Type"])
        val body = apiSpec.body!!.compact()
        assertTrue(body.contains(""""openapi" : "3.0.1""""))
        assertFalse(body.contains("\"null\""))
        assertFalse(body.contains("null,"))

        val openAPI = Json.mapper().readValue(body, OpenAPI::class.java)

        val path = openAPI.paths["/calendar/daysoftheyear"]
        assertNotNull(path)

        val requestBody = path.post.requestBody
        assertTrue(requestBody.content.containsKey("application/json"))

        val mediaType = requestBody.content["application/json"]
        assertNotNull(mediaType)
        assertEquals("#/components/schemas/DaysOfTheYearRequest", mediaType.schema.`$ref`)

        val responseOk = path.post.responses["200"]
        assertNotNull(responseOk)
        //need to assert that FiniteDurableReturnResult is generated as a referenced schema rather than inline content
        assertEquals("#/components/schemas/FiniteDurableReturnResult_of_CalendarDay", responseOk.content["application/json"]!!.schema.`$ref`)

        val compactBody = body.compact()

        // need to assert "items" by contains this way because when serializing the Schema is not delegated to ArraySchema
        assertThat(compactBody).contains(finiteDurableReturnResultRef.compact())
        assertThat(compactBody).contains(schemaDef.compact())

        assertTrue(openAPI.components.schemas.containsKey("FiniteDurableReturnResult_of_CalendarDay"))
        assertThat(compactBody).contains(finiteDurableReturnResultSchemaWithCalendarDayRef.compact())

        assertTrue(openAPI.components.schemas.containsKey("TimeCallDto"))
        val timeCallDto = openAPI.components.schemas["TimeCallDto"]
        assertNotNull(timeCallDto)
        val timeProperty = timeCallDto.properties["time"]
        assertNotNull(timeProperty)
        assertDoesNotThrow { ZonedDateTime.parse(timeProperty.example.toString()) }

        // Check that generic type parameter for `plusOne` is correctly represented
        with(openAPI.paths["/health/plusone"]) {
            assertNotNull(this)
            val parameters = get.parameters
            val schema = parameters[0].schema as ArraySchema
            assertThat(schema.items.type).isEqualTo("string")
        }

        // Check OpenAPI for TestEntity
        with(openAPI.paths["/testentity"]) {
            assertNotNull(this)
            val getParams = get.parameters
            assertEquals("query", getParams[0].name)

            val postParams = post.parameters
            assertTrue(postParams.isEmpty())
            assertEquals(
                "#/components/schemas/CreateRequest",
                post.requestBody.content["application/json"]?.schema?.`$ref`
            )

            val putParams = put.parameters
            assertTrue(putParams.isEmpty())
            assertEquals(
                "#/components/schemas/UpdateRequest",
                put.requestBody.content["application/json"]?.schema?.`$ref`
            )

            val deleteParams = delete.parameters
            assertEquals("query", deleteParams[0].name)
        }

        with(openAPI.paths["/health/stringmethodwithnameinannotation"]) {
            assertNotNull(this)
            val content = post.requestBody.content["application/json"]
            assertNotNull(content)
            val properties = content.schema.properties
            assertEquals(1, properties.size)
            val field = properties["correctName"]
            assertNotNull(field)
            assertEquals("string", field.type)
        }
    }

    @Test
    fun `OpenApi spec json should include correctly formatted multipart file upload endpoints`() {

        val apiSpec = client.call(GET, WebRequest<Any>("swagger.json"))
        assertEquals(HttpStatus.SC_OK, apiSpec.responseStatus)
        assertEquals("application/json", apiSpec.headers["Content-Type"])
        val body = apiSpec.body!!.compact()
        assertTrue(body.contains(""""openapi" : "3.0.1""""))
        assertFalse(body.contains("\"null\""))
        assertFalse(body.contains("null,"))

        val openAPI = Json.mapper().readValue(body, OpenAPI::class.java)

        with(openAPI.paths["/fileupload/upload"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(multipartFormData, "Multipart file upload should be under multipart form-data content in request body.")
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val file = multipartFormData.schema.properties["file"]
            assertNotNull(file)
            assertEquals("string", file.type, "Multipart file type should be a string.")
            assertEquals("binary", file.format, "Multipart file format should be binary.")
            assertFalse(file.nullable)
        }

        with(openAPI.paths["/fileupload/uploadwithname"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(multipartFormData, "Multipart file upload should be under multipart form-data content in request body.")
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(2, multipartFormData.schema.properties.size)
            val fileName = multipartFormData.schema.properties["name"]
            assertNotNull(fileName)
            assertEquals("string", fileName.type, "Multipart file type should be a string.")
            assertFalse(fileName.nullable)
            val file = multipartFormData.schema.properties["file"]
            assertNotNull(file)
            assertEquals("string", file.type, "Multipart file type should be a string.")
            assertEquals("binary", file.format, "Multipart file format should be binary.")
            assertFalse(file.nullable)
        }

        with(openAPI.paths["/fileupload/uploadwithoutparameterannotations"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(multipartFormData, "Multipart file upload should be under multipart form-data content in request body.")
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(2, multipartFormData.schema.properties.size)
            val fileName = multipartFormData.schema.properties["fileName"]
            assertNotNull(fileName)
            assertEquals("string", fileName.type, "Multipart file type should be a string.")
            assertFalse(fileName.nullable)
            val file = multipartFormData.schema.properties["file"]
            assertNotNull(file)
            assertEquals("string", file.type, "Multipart file type should be a string.")
            assertEquals("binary", file.format, "Multipart file format should be binary.")
            assertFalse(file.nullable)
        }

        with(openAPI.paths["/fileupload/fileuploadobject"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(multipartFormData, "Multipart file upload should be under multipart form-data content in request body.")
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val file = multipartFormData.schema.properties["file"]
            assertNotNull(file)
            assertEquals("string", file.type, "Multipart file type should be a string.")
            assertEquals("binary", file.format, "Multipart file format should be binary.")
            assertFalse(file.nullable)
        }

        with(openAPI.paths["/fileupload/multifileuploadobject"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(multipartFormData, "Multipart file upload should be under multipart form-data content in request body.")
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(2, multipartFormData.schema.properties.size)
            val file1 = multipartFormData.schema.properties["file1"]
            assertNotNull(file1)
            assertEquals("string", file1.type, "Multipart file type should be a string.")
            assertEquals("binary", file1.format, "Multipart file format should be binary.")
            assertFalse(file1.nullable)
            val file2 = multipartFormData.schema.properties["file2"]
            assertNotNull(file2)
            assertEquals("string", file2.type, "Multipart file type should be a string.")
            assertEquals("binary", file2.format, "Multipart file format should be binary.")
            assertFalse(file2.nullable)
        }

        with(openAPI.paths["/fileupload/multiinputstreamfileupload"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(multipartFormData, "Multipart file upload should be under multipart form-data content in request body.")
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(2, multipartFormData.schema.properties.size)
            val file1 = multipartFormData.schema.properties["file1"]
            assertNotNull(file1)
            assertEquals("string", file1.type, "Multipart file type should be a string.")
            assertEquals("binary", file1.format, "Multipart file format should be binary.")
            assertFalse(file1.nullable)
            val file2 = multipartFormData.schema.properties["file2"]
            assertNotNull(file2)
            assertEquals("string", file2.type, "Multipart file type should be a string.")
            assertEquals("binary", file2.format, "Multipart file format should be binary.")
            assertFalse(file2.nullable)
        }

        with(openAPI.paths["/fileupload/fileuploadobjectlist"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(multipartFormData, "Multipart file upload should be under multipart form-data content in request body.")
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val files = multipartFormData.schema.properties["files"]
            assertNotNull(files)
            assertTrue(files is ArraySchema)
            assertEquals("string", files.items.type)
            assertEquals("binary", files.items.format)
            assertFalse(files.items.nullable)
        }

        with(openAPI.paths["/fileupload/uploadwithqueryparam"]) {
            assertNotNull(this)
            assertEquals(1, post.parameters.size)
            val queryParam = post.parameters.first()
            assertEquals("tenant", queryParam.name)
            assertFalse(queryParam.required)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(multipartFormData, "Multipart file upload should be under multipart form-data content in request body.")
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val file = multipartFormData.schema.properties["file"]
            assertNotNull(file)
            assertEquals("string", file.type)
            assertEquals("binary", file.format)
            assertFalse(file.nullable)
        }

        with(openAPI.paths["/fileupload/uploadwithpathparam/{tenant}"]) {
            assertNotNull(this)
            assertEquals(1, post.parameters.size)
            val queryParam = post.parameters.first()
            assertEquals("tenant", queryParam.name)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(multipartFormData, "Multipart file upload should be under multipart form-data content in request body.")
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val file = multipartFormData.schema.properties["file"]
            assertNotNull(file)
            assertEquals("string", file.type)
            assertEquals("binary", file.format)
            assertFalse(file.nullable)
        }

        with(openAPI.paths["/fileupload/uploadwithnameinannotation"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(multipartFormData, "Multipart file upload should be under multipart form-data content in request body.")
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val file = multipartFormData.schema.properties["differentName"]
            assertNotNull(file)
            assertEquals("string", file.type)
            assertEquals("binary", file.format)
            assertFalse(file.nullable)
        }
    }

    @Test
    fun `GET swagger UI should return html with reference to swagger json`() {

        val apiSpec = client.call(GET, WebRequest<Any>("swagger"))
        assertEquals(HttpStatus.SC_OK, apiSpec.responseStatus)
        assertEquals("text/html", apiSpec.headers["Content-Type"])
        val expected = """url: "/${context.basePath}/v${context.version}/swagger.json""""
        assertTrue(apiSpec.body!!.contains(expected))
    }

    @Test
    fun `GET swagger UI dependencies should return non empty result`() {
        val baseClient = TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/")
        val swaggerUIversion = OptionalDependency.SWAGGERUI.version
        val swagger = baseClient.call(GET, WebRequest<Any>("api/v1/swagger"))
        val swaggerUIBundleJS = baseClient.call(GET, WebRequest<Any>("webjars/swagger-ui/$swaggerUIversion/swagger-ui-bundle.js"))
        val swaggerUIcss = baseClient.call(GET, WebRequest<Any>("webjars/swagger-ui/$swaggerUIversion/swagger-ui-bundle.js"))

        assertEquals(HttpStatus.SC_OK, swagger.responseStatus)
        assertEquals(HttpStatus.SC_OK, swaggerUIBundleJS.responseStatus)
        assertEquals(HttpStatus.SC_OK, swaggerUIcss.responseStatus)
        assertNotNull(swaggerUIBundleJS.body)
        assertNotNull(swaggerUIcss.body)
    }

    private val schemaDef =  """"CalendarDay" : {
        "required" : [ "dayOfWeek", "dayOfYear" ],
        "type" : "object",
        "properties" : {
          "dayOfWeek" : {
            "nullable" : false,
            "example" : "TUESDAY",
            "enum" : [ "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY" ]
          },
          "dayOfYear" : {
            "type" : "string",
            "nullable" : false,
            "example" : "string"
          }
        },
        "nullable" : false
      }""".trimIndent()

    private val finiteDurableReturnResultSchemaWithCalendarDayRef =  """"FiniteDurableReturnResult_of_CalendarDay" : {
        "required" : [ "isLastResult", "positionedValues" ],
        "type" : "object",
        "properties" : {
          "isLastResult" : {
            "type" : "boolean",
            "nullable" : false,
            "example" : true
          },
          "positionedValues" : {
            "uniqueItems" : false,
            "type" : "array",
            "nullable" : false,
            "items" : {
              "type" : "object",
              "properties" : {
                "position" : {
                  "type" : "integer",
                  "format" : "int64",
                  "nullable" : false,
                  "example" : 0
                },
                "value" : {
                  "${"$"}ref" : "#/components/schemas/CalendarDay"
                }
              },
              "nullable" : false,
              "example" : "No example available for this type"
            }
          }""".trimIndent()

    private val finiteDurableReturnResultRef = """
         ref" : "#/components/schemas/FiniteDurableReturnResult_of_CalendarDay
        """.trimIndent()
}
