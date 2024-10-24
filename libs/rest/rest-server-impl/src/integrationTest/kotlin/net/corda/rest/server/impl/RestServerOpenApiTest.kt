package net.corda.rest.server.impl

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.server.impl.internal.OptionalDependency
import net.corda.rest.server.impl.utils.compact
import net.corda.rest.test.ObjectsInJsonEndpointImpl
import net.corda.rest.test.TestEntityRestResourceImpl
import net.corda.rest.test.TestFileUploadImpl
import net.corda.rest.test.TestHealthCheckAPIImpl
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.WebRequest
import net.corda.rest.test.utils.multipartDir
import net.corda.rest.tools.HttpVerb.GET
import net.corda.utilities.NetworkHostAndPort
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestServerOpenApiTest : RestServerTestBase() {
    companion object {
        private val restServerSettings = RestServerSettings(
            NetworkHostAndPort("localhost", 0),
            context,
            null,
            null,
            RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
            20000L
        )

        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            server = RestServerImpl(
                listOf(
                    TestHealthCheckAPIImpl(),
                    TestEntityRestResourceImpl(),
                    TestFileUploadImpl(),
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
                "#/components/schemas/CreationParams",
                post.requestBody.content["application/json"]?.schema?.`$ref`
            )

            val putParams = put.parameters
            assertTrue(putParams.isEmpty())
            assertEquals(
                "#/components/schemas/UpdateParams",
                put.requestBody.content["application/json"]?.schema?.`$ref`
            )

            val deleteParams = delete.parameters
            assertEquals("query", deleteParams[0].name)
        }
        with(openAPI.paths["/testentity/inputecho"]) {
            assertNotNull(this)
            val putParams = put.parameters
            assertTrue(putParams.isEmpty())
            assertEquals(
                "#/components/schemas/EchoParams",
                put.requestBody.content["application/json"]?.schema?.`$ref`
            )

            assertEquals(
                "#/components/schemas/EchoResponse",
                put.responses["200"]?.content?.get("application/json")?.schema?.`$ref`
            )
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

        with(openAPI.paths["/health/apireturningnullobject"]) {
            assertNotNull(this)
            val post = openAPI.paths["/health/apireturningnullobject"]!!.post
            assertNotNull(post)
            val successResponse = post.responses["200"]
            assertNotNull(successResponse)
            val content = successResponse.content["application/json"]
            assertNotNull(content)
            val schema = content.schema as ComposedSchema
            assertTrue(schema.nullable, "The schema should have the nullable property")
            assertEquals(1, schema.allOf.size)
            val wrappedObject = schema.allOf[0]
            assertNull(wrappedObject.nullable, "The wrapped object itself shouldn't be nullable")
            assertEquals("#/components/schemas/SomeTestNullableType", wrappedObject.`$ref`)
        }

        with(openAPI.paths["/health/apireturningnullstring"]) {
            assertNotNull(this)
            val post = openAPI.paths["/health/apireturningnullstring"]!!.post
            assertNotNull(post)
            val successResponse = post.responses["200"]
            assertNotNull(successResponse)
            val content = successResponse.content["application/json"]
            assertNotNull(content)
            val schema = content.schema
            assertTrue(schema.nullable, "The schema should have the nullable property")
            assertEquals("string", schema.type)
        }

        with(openAPI.components.schemas["EchoParams"]) {
            assertNotNull(this)
            assertNull(this.nullable)

            val contentProperty = this.properties["content"]
            assertThat(contentProperty?.description).isEqualTo("Either nested JSON object or a valid JSON-escaped string.")
        }

        with(openAPI.components.schemas["EchoResponse"]) {
            assertNotNull(this)
            assertNull(this.nullable)

            val contentProperty = this.properties["content"]
            assertThat(contentProperty?.description).isEqualTo("Either nested JSON object or a valid JSON-escaped string.")
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
            assertNotNull(
                multipartFormData,
                "Multipart file upload should be under multipart form-data content in request body."
            )
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val file = multipartFormData.schema.properties["file"]
            assertNotNull(file)
            assertEquals("string", file.type, "Multipart file type should be a string.")
            assertEquals("binary", file.format, "Multipart file format should be binary.")
            assertFalse(file.nullable)
            assertEquals("A content of the file to upload.", file.description, "File upload should have a description.")
        }

        with(openAPI.paths["/fileupload/uploadwithname"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(
                multipartFormData,
                "Multipart file upload should be under multipart form-data content in request body."
            )
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
            assertEquals("A content of the file to upload.", file.description, "File upload should have a description.")
        }

        with(openAPI.paths["/fileupload/uploadwithoutparameterannotations"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(
                multipartFormData,
                "Multipart file upload should be under multipart form-data content in request body."
            )
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
            assertEquals("A content of the file to upload.", file.description, "File upload should have a description.")
        }

        with(openAPI.paths["/fileupload/fileuploadobject"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(
                multipartFormData,
                "Multipart file upload should be under multipart form-data content in request body."
            )
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val file = multipartFormData.schema.properties["file"]
            assertNotNull(file)
            assertEquals("string", file.type, "Multipart file type should be a string.")
            assertEquals("binary", file.format, "Multipart file format should be binary.")
            assertFalse(file.nullable)
            assertEquals("A content of the file to upload.", file.description, "File upload should have a description.")
        }

        with(openAPI.paths["/fileupload/multifileuploadobject"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(
                multipartFormData,
                "Multipart file upload should be under multipart form-data content in request body."
            )
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(2, multipartFormData.schema.properties.size)
            val file1 = multipartFormData.schema.properties["file1"]
            assertNotNull(file1)
            assertEquals("string", file1.type, "Multipart file type should be a string.")
            assertEquals("binary", file1.format, "Multipart file format should be binary.")
            assertFalse(file1.nullable)
            assertEquals(
                "A content of the file to upload.",
                file1.description,
                "File upload should have a description."
            )
            val file2 = multipartFormData.schema.properties["file2"]
            assertNotNull(file2)
            assertEquals("string", file2.type, "Multipart file type should be a string.")
            assertEquals("binary", file2.format, "Multipart file format should be binary.")
            assertFalse(file2.nullable)
            assertEquals(
                "A content of the file to upload.",
                file2.description,
                "File upload should have a description."
            )
        }

        with(openAPI.paths["/fileupload/multiinputstreamfileupload"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(
                multipartFormData,
                "Multipart file upload should be under multipart form-data content in request body."
            )
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(2, multipartFormData.schema.properties.size)
            val file1 = multipartFormData.schema.properties["file1"]
            assertNotNull(file1)
            assertEquals("string", file1.type, "Multipart file type should be a string.")
            assertEquals("binary", file1.format, "Multipart file format should be binary.")
            assertFalse(file1.nullable)
            assertEquals(
                "A content of the file to upload.",
                file1.description,
                "File upload should have a description."
            )
            val file2 = multipartFormData.schema.properties["file2"]
            assertNotNull(file2)
            assertEquals("string", file2.type, "Multipart file type should be a string.")
            assertEquals("binary", file2.format, "Multipart file format should be binary.")
            assertFalse(file2.nullable)
            assertEquals(
                "A content of the file to upload.",
                file2.description,
                "File upload should have a description."
            )
        }

        with(openAPI.paths["/fileupload/fileuploadobjectlist"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(
                multipartFormData,
                "Multipart file upload should be under multipart form-data content in request body."
            )
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val files = multipartFormData.schema.properties["files"]
            assertNotNull(files)
            assertFalse(files.nullable)
            assertTrue(files is ArraySchema)
            assertEquals("string", files.items.type)
            assertEquals("binary", files.items.format)
        }

        with(openAPI.paths["/fileupload/uploadwithqueryparam"]) {
            assertNotNull(this)
            assertEquals(1, post.parameters.size)
            val queryParam = post.parameters.first()
            assertEquals("tenant", queryParam.name)
            assertFalse(queryParam.required)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(
                multipartFormData,
                "Multipart file upload should be under multipart form-data content in request body."
            )
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val file = multipartFormData.schema.properties["file"]
            assertNotNull(file)
            assertEquals("string", file.type)
            assertEquals("binary", file.format)
            assertFalse(file.nullable)
            assertEquals("A content of the file to upload.", file.description, "File upload should have a description.")
        }

        with(openAPI.paths["/fileupload/uploadwithpathparam/{tenant}"]) {
            assertNotNull(this)
            assertEquals(1, post.parameters.size)
            val queryParam = post.parameters.first()
            assertEquals("tenant", queryParam.name)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(
                multipartFormData,
                "Multipart file upload should be under multipart form-data content in request body."
            )
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val file = multipartFormData.schema.properties["file"]
            assertNotNull(file)
            assertEquals("string", file.type)
            assertEquals("binary", file.format)
            assertFalse(file.nullable)
            assertEquals("A content of the file to upload.", file.description, "File upload should have a description.")
        }

        with(openAPI.paths["/fileupload/uploadwithnameinannotation"]) {
            assertNotNull(this)
            val multipartFormData = post.requestBody.content["multipart/form-data"]
            assertNotNull(
                multipartFormData,
                "Multipart file upload should be under multipart form-data content in request body."
            )
            assertEquals("object", multipartFormData.schema.type, "Multipart file content should be in an object.")
            assertEquals(1, multipartFormData.schema.properties.size)
            val file = multipartFormData.schema.properties["differentName"]
            assertNotNull(file)
            assertEquals("string", file.type)
            assertEquals("binary", file.format)
            assertFalse(file.nullable)
            assertEquals("differentDesc", file.description, "File upload should have a description.")
        }
    }

    @Test
    fun `OpenApi spec json should include correctly formatted json objects including nullability`() {
        val apiSpec = client.call(GET, WebRequest<Any>("swagger.json"))
        val body = apiSpec.body!!.compact()

        val openAPI = Json.mapper().readValue(body, OpenAPI::class.java)

        fun assertJsonObject(jsonObject: Schema<*>?, nullable: Boolean? = false) {
            assertNotNull(jsonObject)
            assertEquals("object", jsonObject.type)
            assertNull(jsonObject.format)
            assertEquals("Either nested JSON object or a valid JSON-escaped string.", jsonObject.description)
            assertEquals("{\"command\":\"echo\", \"data\":{\"value\": \"hello-world\"}}", jsonObject.example)
            assertEquals(nullable, jsonObject.nullable)
        }

        with(openAPI.paths["/objects-in-json-endpoint/create-with-one-object"]) {
            assertNotNull(this)
            val requestSchema = post.requestBody.content["application/json"]!!.schema
            assertEquals("#/components/schemas/RequestWithJsonObject", requestSchema.`$ref`)
        }

        with(openAPI.components.schemas["RequestWithJsonObject"]) {
            assertNotNull(this)
            assertEquals(listOf("id", "obj"), this.required)
            assertEquals(2, this.properties.size)
            assertNotNull(this.properties["id"])
            val jsonObject = this.properties["obj"]
            assertJsonObject(jsonObject)
        }

        with(openAPI.paths["/objects-in-json-endpoint/create-with-individual-params"]) {
            assertNotNull(this)
            val requestSchema = post.requestBody.content["application/json"]!!.schema
            assertEquals("#/components/schemas/CreateWithIndividualParamsWrapperRequest", requestSchema.`$ref`)
        }

        with(openAPI.components.schemas["CreateWithIndividualParamsWrapperRequest"]) {
            assertNotNull(this)
            assertEquals(listOf("id", "obj"), this.required)
            assertEquals(2, this.properties.size)
            assertNotNull(this.properties["id"])
            val jsonObject = this.properties["obj"]
            assertJsonObject(jsonObject)
        }

        with(openAPI.paths["/objects-in-json-endpoint/nullable-json-object-in-request"]) {
            assertNotNull(this)
            val requestSchema = post.requestBody.content["application/json"]!!.schema
            assertEquals("#/components/schemas/NullableJsonObjectInRequestWrapperRequest", requestSchema.`$ref`)
            val responseSchema = post.responses["200"]!!.content["application/json"]!!.schema
            assertEquals("#/components/schemas/ResponseWithJsonObjectNullable", responseSchema.`$ref`)
        }

        with(openAPI.components.schemas["NullableJsonObjectInRequestWrapperRequest"]) {
            assertNotNull(this)
            assertEquals(listOf("id"), this.required)
            assertEquals(2, this.properties.size)
            assertNotNull(this.properties["id"])
            val jsonObject = this.properties["obj"]
            assertJsonObject(jsonObject, true)
        }

        with(openAPI.components.schemas["ResponseWithJsonObjectNullable"]) {
            assertNotNull(this)
            assertEquals(listOf("id"), this.required)
            assertEquals(2, this.properties.size)
            assertNotNull(this.properties["id"])
            val jsonObject = this.properties["obj"]
            assertJsonObject(jsonObject, true)
        }
    }

    @Test
    fun `GET swagger UI should return html with reference to swagger json`() {
        val apiSpec = client.call(GET, WebRequest<Any>("swagger"))
        assertEquals(HttpStatus.SC_OK, apiSpec.responseStatus)
        assertEquals("text/html;charset=utf-8", apiSpec.headers["Content-Type"])
        val expected = """url: "/${context.basePath}/${apiVersion.versionPath}/swagger.json""""
        assertTrue(apiSpec.body!!.contains(expected))
    }

    @Test
    fun `GET swagger UI with trailing slash in path should return html with reference to swagger json without trailing slash`() {
        val apiSpec = client.call(GET, WebRequest<Any>("swagger/"))
        assertEquals(HttpStatus.SC_OK, apiSpec.responseStatus)
        assertEquals("text/html;charset=utf-8", apiSpec.headers["Content-Type"])
        val expected = """url: "/${context.basePath}/${apiVersion.versionPath}/swagger.json""""
        assertTrue(apiSpec.body!!.contains(expected))
    }

    @Test
    fun `GET swagger UI dependencies should return non empty result`() {
        val baseClient = TestHttpClientUnirestImpl("http://${restServerSettings.address.host}:${server.port}/")
        val swaggerUIversion = OptionalDependency.SWAGGERUI.version
        val swagger = baseClient.call(GET, WebRequest<Any>("api/${apiVersion.versionPath}/swagger"))
        val swaggerUIBundleJS =
            baseClient.call(GET, WebRequest<Any>("webjars/swagger-ui/$swaggerUIversion/swagger-ui-bundle.js"))
        val swaggerUIcss = baseClient.call(GET, WebRequest<Any>("webjars/swagger-ui/$swaggerUIversion/swagger-ui.css"))

        assertEquals(HttpStatus.SC_OK, swagger.responseStatus)
        assertEquals(HttpStatus.SC_OK, swaggerUIBundleJS.responseStatus)
        assertEquals(HttpStatus.SC_OK, swaggerUIcss.responseStatus)
        assertNotNull(swaggerUIBundleJS.body)
        assertNotNull(swaggerUIcss.body)
    }

    private val schemaDef = """"CalendarDay" : {
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
        }
      }
    """.trimIndent()
}
