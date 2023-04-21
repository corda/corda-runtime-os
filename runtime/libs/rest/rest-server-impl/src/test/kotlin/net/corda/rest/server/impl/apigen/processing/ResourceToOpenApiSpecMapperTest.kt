package net.corda.rest.server.impl.apigen.processing

import io.swagger.v3.oas.models.media.ArraySchema
import java.io.InputStream
import net.corda.rest.server.impl.apigen.models.Endpoint
import net.corda.rest.server.impl.apigen.models.EndpointMethod
import net.corda.rest.server.impl.apigen.models.EndpointParameter
import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.models.InvocationMethod
import net.corda.rest.server.impl.apigen.models.ParameterType
import net.corda.rest.server.impl.apigen.models.ResponseBody
import net.corda.rest.server.impl.apigen.processing.openapi.schema.DefaultSchemaModelProvider
import net.corda.rest.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.rest.server.impl.apigen.processing.openapi.schema.SchemaModelContextHolder
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaCollectionModel
import net.corda.rest.server.impl.apigen.processing.openapi.toOpenAPI
import net.corda.rest.server.impl.apigen.processing.openapi.toOpenApiParameter
import net.corda.rest.server.impl.apigen.processing.openapi.toOperation
import net.corda.rest.server.impl.apigen.processing.openapi.toRequestBody
import net.corda.rest.server.impl.apigen.processing.openapi.toValidMethodName
import net.corda.rest.server.impl.utils.getHealthCheckApiTestResource
import net.corda.rest.test.TestHealthCheckAPI
import net.corda.rest.test.TestHealthCheckAPIImpl
import net.corda.rest.tools.HttpPathUtils.joinResourceAndEndpointPaths
import net.corda.rest.tools.HttpPathUtils.toOpenApiPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod
import net.corda.rest.JsonObject
import net.corda.rest.test.TestFileUploadAPI
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull

class ResourceToOpenApiSpecMapperTest {

    @Test
    fun `Can convert query parameter to OpenApiParameter`() {
        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val queryParameter = EndpointParameter(
            id = "id",
            name = "name",
            description = "description",
            required = true,
            default = "default",
            classType = String::class.java,
            type = ParameterType.QUERY
        )
        val openApiQueryParameter = queryParameter.toOpenApiParameter(schemaModelProvider)
        assertEquals("name", openApiQueryParameter.name)
        assertEquals("description", openApiQueryParameter.description)
        assertEquals(true, openApiQueryParameter.required)
        assertEquals("query", openApiQueryParameter.`in`)
        assertEquals("string", openApiQueryParameter.schema.type)
        assertEquals(null, openApiQueryParameter.schema.format)
    }

    @Test
    fun `Can convert query parameter without name to OpenApiParameter`() {
        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val queryParameter = EndpointParameter(
            id = "id",
            name = "",
            description = "description",
            required = true,
            default = "default",
            classType = String::class.java,
            type = ParameterType.QUERY
        )
        val openApiQueryParameter = queryParameter.toOpenApiParameter(schemaModelProvider)
        assertEquals("id", openApiQueryParameter.name)
        assertEquals("description", openApiQueryParameter.description)
        assertEquals(true, openApiQueryParameter.required)
        assertEquals("query", openApiQueryParameter.`in`)
        assertEquals("string", openApiQueryParameter.schema.type)
        assertEquals(null, openApiQueryParameter.schema.format)
    }

    @Test
    fun `Can convert body with nested parameterized types to RequestBody`() {
        @Suppress("unused")
        class TestParamClass(
            val a: String,
            val b: List<List<Boolean>>
        )

        val bodyParameter = EndpointParameter(
            id = "id",
            name = "name",
            description = "description",
            required = true,
            default = "default",
            classType = List::class.java,
            parameterizedTypes = listOf(GenericParameterizedType(TestParamClass::class.java)),
            type = ParameterType.BODY
        )
        val schemaModelContextHolder = SchemaModelContextHolder()
        val schemaModelProvider = DefaultSchemaModelProvider(schemaModelContextHolder)

        val requestBody = listOf(bodyParameter).toRequestBody(schemaModelProvider, "testSchemaName")
        val schema = requestBody!!.content["application/json"]!!.schema.properties["name"]!!

        assertEquals("array", schema.type)
        assertEquals(null, schema.format)
        with((schema as ArraySchema).items) {
            assertEquals(
                "#/components/schemas/TestParamClass",
                `$ref`
            )
        }

        with(schemaModelContextHolder.getSchema(ParameterizedClass(TestParamClass::class.java))!!) {
            assertEquals(DataType.OBJECT, type)
            assertEquals(DataType.STRING, properties["a"]!!.type)
            assertEquals("string", properties["a"]!!.example)
            assertEquals(DataType.ARRAY, properties["b"]!!.type)
            // verify nested parameterized types support
            assertEquals(DataType.ARRAY, (properties["b"]!! as SchemaCollectionModel).items!!.type)
            assertEquals(DataType.BOOLEAN, ((properties["b"]!! as SchemaCollectionModel).items!! as SchemaCollectionModel).items!!.type)
        }
    }

    @Test
    fun `Can convert body parameter to RequestBody`() {
        val bodyParameter = EndpointParameter(
            id = "id",
            name = "name",
            description = "description",
            required = true,
            default = "default",
            classType = String::class.java,
            type = ParameterType.BODY
        )
        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val requestBody = listOf(bodyParameter).toRequestBody(schemaModelProvider, "testSchemaName")

        assertEquals(true, requestBody!!.required)
        assertTrue(requestBody.content.containsKey("application/json"))
        val content = requestBody.content["application/json"]
        assertTrue(content!!.schema.properties.containsKey("name"))
        assertEquals(content.schema.type, "object")
        val property = content.schema.properties["name"]
        assertEquals("string", property!!.example)
        assertEquals("string", property.type)
    }

    @Test
    fun `Can convert body parameter with empty name`() {
        val bodyParameter = EndpointParameter(
            id = "id",
            name = "",
            description = "description",
            required = true,
            default = "default",
            classType = String::class.java,
            type = ParameterType.BODY
        )
        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val requestBody = listOf(bodyParameter).toRequestBody(schemaModelProvider, "testSchemaName")

        assertEquals(true, requestBody!!.required)
        assertTrue(requestBody.content.containsKey("application/json"))
        val content = requestBody.content["application/json"]
        assertTrue(content!!.schema.properties.containsKey("id"))
        assertEquals(content.schema.type, "object")
        val property = content.schema.properties["id"]
        assertEquals("string", property!!.example)
        assertEquals("string", property.type)
    }

    @Test
    fun `Can convert resource list to OpenApi object`() {
        val resource = getHealthCheckApiTestResource()
        val openAPI = listOf(resource).toOpenAPI(SchemaModelContextHolder())
        with(openAPI) {

            val tag = tags.single()
            assertEquals("HealthCheckAPI", tag.name)
            assertEquals("Health Check", tag.description)

            resource.endpoints.forEach {
                val openApiPath = joinResourceAndEndpointPaths(resource.path, it.path).toOpenApiPath()
                assertTrue(paths.containsKey(openApiPath))
            }

            paths.values.flatMap { listOfNotNull(it.get, it.post) }.forEach {
                assertEquals("HealthCheckAPI", it.tags.single())
            }


            assertTrue(components.schemas.containsKey("PingPongData"))

            assertEquals(
                "#/components/schemas/PingPongData",
                this.paths["/health/ping"]!!.post.requestBody.content["application/json"]!!.schema.`$ref`
            )
        }
    }

    @Test
    fun `endpointToOperation withListResponse OpenApiCorrectResponseWithGenerics`() {
        val endpoint = Endpoint(
            method = EndpointMethod.POST,
            title = "plusOne",
            description = "",
            path = "plusOne",
            parameters = listOf(
                EndpointParameter(
                    id = "data",
                    description = "Data",
                    name = "",
                    required = false,
                    classType = List::class.java,
                    parameterizedTypes = listOf(
                        GenericParameterizedType(
                            clazz = List::class.java,
                            nestedParameterizedTypes = listOf(GenericParameterizedType(Int::class.java))
                        )
                    ),
                    type = ParameterType.BODY,
                    default = ""
                )
            ),
            responseBody = ResponseBody(
                description = "", type = List::class.java, parameterizedTypes = listOf(
                    GenericParameterizedType(Double::class.java)
                )
            ),
            invocationMethod = InvocationMethod(method = TestHealthCheckAPI::plusOne.javaMethod!!, instance = TestHealthCheckAPIImpl())
        )
        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val openApi = endpoint.toOperation("path", schemaModelProvider)

        with(openApi.responses["200"]!!.content["application/json"]!!.schema as ArraySchema) {
            assertEquals("array", type)
            assertEquals("number", items.type)
            assertEquals("double", items.format)

        }
    }

    @Test
    fun `endpointToOperation withVoidResponse OpenApiCorrectResponseWithNoResponseBody`() {
        val endpoint = Endpoint(
            method = EndpointMethod.POST,
            title = "plusOne",
            description = "",
            path = "plusOne",
            parameters = emptyList(),
            responseBody = ResponseBody(description = "", type = Void.TYPE, parameterizedTypes = emptyList()),
            invocationMethod = InvocationMethod(method = TestHealthCheckAPI::voidResponse.javaMethod!!, instance = TestHealthCheckAPIImpl())
        )

        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val openApi = endpoint.toOperation("path", schemaModelProvider)

        with(openApi.responses["200"]!!) {
            assertNull(content)
        }
    }

    @Test
    fun `endpointToOperation withVoidClassResponse OpenApiCorrectResponseWithNoResponseBody`() {
        val endpoint = Endpoint(
            method = EndpointMethod.POST,
            title = "plusOne",
            description = "",
            path = "plusOne",
            parameters = emptyList(),
            responseBody = ResponseBody(description = "", type = Void::class.java, parameterizedTypes = emptyList()),
            invocationMethod = InvocationMethod(method = TestHealthCheckAPI::voidResponse.javaMethod!!, instance = TestHealthCheckAPIImpl())
        )

        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val openApi = endpoint.toOperation("path", schemaModelProvider)

        with(openApi.responses["200"]!!) {
            assertNull(content)
        }
    }

    @Test
    fun `endpointToOperation generates proper OperationID`() {
        val endpoint = Endpoint(
            method = EndpointMethod.POST,
            title = "plusOne",
            description = "",
            path = "plusOne",
            parameters = emptyList(),
            responseBody = ResponseBody(description = "", type = Void::class.java, parameterizedTypes = emptyList()),
            invocationMethod = InvocationMethod(method = TestHealthCheckAPI::voidResponse.javaMethod!!, instance = TestHealthCheckAPIImpl())
        )

        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val operation = endpoint.toOperation(
            joinResourceAndEndpointPaths("HealthCheckAPI", endpoint.path).toOpenApiPath(), schemaModelProvider)
        assertEquals("post_healthcheckapi_plusone", operation.operationId)
    }

    @Test
    fun `Invalid characters should be replaced when generating method names`() {
        val path = "resource/call{pathVariable}"
        assertEquals("resource_call_pathvariable_", path.toValidMethodName())
    }

    @Test
    fun `endpoint with multipart generates correct open api request content`() {
        val endpoint = Endpoint(
            method = EndpointMethod.POST,
            title = "upload",
            description = "",
            path = "fileupload",
            parameters = listOf(
                EndpointParameter(
                    id = "file_id",
                    description = "The file input stream.",
                    name = "file",
                    required = true,
                    classType = InputStream::class.java,
                    parameterizedTypes = emptyList(),
                    type = ParameterType.BODY,
                    default = "",
                    isFile = true
                )
            ),
            responseBody = ResponseBody(
                description = "", type = String::class.java, parameterizedTypes = emptyList()
            ),
            invocationMethod = InvocationMethod(method = TestFileUploadAPI::upload.javaMethod!!, instance = TestHealthCheckAPIImpl())
        )
        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val openApi = endpoint.toOperation("path", schemaModelProvider)

        assertNotNull(openApi.requestBody)

        val multipartFormData = openApi.requestBody.content["multipart/form-data"]
        assertNotNull(multipartFormData)

        assertEquals("object", multipartFormData!!.schema.type)
        val properties = multipartFormData.schema.properties
        assertEquals(1, properties.size)

        // open-api generation uses the name if its present as the property name
        val file = properties["file"]
        assertNotNull(file)
        assertEquals("file", file!!.name)
        assertEquals("string", file.type)
        assertEquals("binary", file.format)
        assertEquals("The file input stream.", file.description)
        assertFalse(file.nullable)
    }

    @Test
    fun `endpoint with multiple body params including 1 input stream generates request body with multipart content`() {
        val endpoint = Endpoint(
            method = EndpointMethod.POST,
            title = "Upload a file with its name",
            description = "",
            path = "",
            parameters = listOf(
                EndpointParameter(
                    id = "filename_id",
                    name = "filename",
                    description = "Name of the file.",
                    required = true,
                    classType = String::class.java,
                    type = ParameterType.BODY,
                    default = "default"
                ),
                EndpointParameter(
                    id = "file_id",
                    name = "file",
                    description = "The file input stream.",
                    required = true,
                    classType = InputStream::class.java,
                    type = ParameterType.BODY,
                    default = "",
                    isFile = true
                )
            ),
            responseBody = ResponseBody(
                description = "", type = String::class.java, parameterizedTypes = emptyList()
            ),
            invocationMethod = InvocationMethod(
                method = TestFileUploadAPI::uploadWithName.javaMethod!!,
                instance = TestHealthCheckAPIImpl()
            )
        )
        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val openApi = endpoint.toOperation("fileupload", schemaModelProvider)

        assertNotNull(openApi.requestBody)

        val multipartFormData = openApi.requestBody.content["multipart/form-data"]
        assertNotNull(multipartFormData)

        assertEquals("object", multipartFormData!!.schema.type)
        val properties = multipartFormData.schema.properties
        assertEquals(2, properties.size)

        val filename = properties["filename"]
        assertNotNull(filename)
        assertEquals("filename", filename!!.name)
        assertEquals("string", filename.type)
        assertEquals("Name of the file.", filename.description)
        assertEquals("string", filename.example)
        assertFalse(filename.nullable)

        val file = properties["file"]
        assertNotNull(file)
        assertEquals("file", file!!.name)
        assertEquals("string", file.type)
        assertEquals("binary", file.format)
        assertEquals("The file input stream.", file.description)
        assertFalse(file.nullable)
    }

    @Test
    fun `Can convert JsonObject parameter to OpenApiParameter`() {
        val schemaModelProvider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val queryParameter = EndpointParameter(
            id = "id",
            name = "name",
            description = "description",
            required = true,
            default = "default",
            classType = JsonObject::class.java,
            type = ParameterType.BODY
        )
        val openApiQueryParameter = queryParameter.toOpenApiParameter(schemaModelProvider)
        assertEquals("name", openApiQueryParameter.name)
        assertEquals("description", openApiQueryParameter.description)
        assertEquals(true, openApiQueryParameter.required)
        assertEquals("body", openApiQueryParameter.`in`)
        assertEquals(null, openApiQueryParameter.schema.type)
        assertEquals(null, openApiQueryParameter.schema.format)
        assertEquals("Can be any value - string, number, boolean, array or object.", openApiQueryParameter.schema.description)
        assertEquals("{\"command\":\"echo\", \"data\":{\"value\": \"hello-world\"}}",
            openApiQueryParameter.schema.example)
    }
}