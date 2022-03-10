package net.corda.httprpc.server.impl.apigen.processing

import io.swagger.v3.oas.models.media.ArraySchema
import net.corda.httprpc.server.impl.apigen.models.Endpoint
import net.corda.httprpc.server.impl.apigen.models.EndpointMethod
import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.models.InvocationMethod
import net.corda.httprpc.server.impl.apigen.models.ParameterType
import net.corda.httprpc.server.impl.apigen.models.ResponseBody
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.DefaultSchemaModelProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelContextHolder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaCollectionModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.toOpenAPI
import net.corda.httprpc.server.impl.apigen.processing.openapi.toOpenApiParameter
import net.corda.httprpc.server.impl.apigen.processing.openapi.toOperation
import net.corda.httprpc.server.impl.apigen.processing.openapi.toRequestBody
import net.corda.httprpc.server.impl.apigen.processing.openapi.toValidMethodName
import net.corda.httprpc.server.impl.utils.getHealthCheckApiTestResource
import net.corda.httprpc.test.TestHealthCheckAPI
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.tools.HttpPathUtils.joinResourceAndEndpointPaths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

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
        val schema = requestBody!!.content["application/json"]!!.schema.properties["id"]!!

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
                val openApiPath = joinResourceAndEndpointPaths(resource.path, it.path)
                assertTrue(paths.containsKey(openApiPath))
            }

            paths.values.flatMap { listOfNotNull(it.get, it.post) }.forEach {
                assertEquals("HealthCheckAPI", it.tags.single())
            }


            assert(components.schemas.containsKey("PingPongData"))

            assertEquals(
                "#/components/schemas/PingRequest",
                this.paths["/health/ping"]!!.post.requestBody.content["application/json"]!!.schema.`$ref`
            )
        }
    }

    @Test
    fun `endpointToOperation_withListResponse_OpenApiCorrectResponseWithGenerics`() {
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
    fun `endpointToOperation_withVoidResponse_OpenApiCorrectResponseWithNoResponseBody`() {
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
    fun `endpointToOperation_withVoidClassResponse_OpenApiCorrectResponseWithNoResponseBody`() {
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
    fun `endpointToOperation_generates_proper_OperationID`() {
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
        val operation = endpoint.toOperation(joinResourceAndEndpointPaths("HealthCheckAPI", endpoint.path), schemaModelProvider)
        assertEquals("post_healthcheckapi_plusone", operation.operationId)
    }

    @Test
    fun `Invalid characters should be replaced when generating method names`() {
        val path = "resource/call{pathVariable}"
        assertEquals("resource_call_pathvariable_", path.toValidMethodName())
    }
}