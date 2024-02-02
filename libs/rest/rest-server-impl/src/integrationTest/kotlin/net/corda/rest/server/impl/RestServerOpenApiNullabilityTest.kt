package net.corda.rest.server.impl

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.server.impl.utils.compact
import net.corda.rest.test.NullabilityRestResourceImpl
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestServerOpenApiNullabilityTest : RestServerTestBase() {
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
                    NullabilityRestResourceImpl(),
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

        with(openAPI.paths["/nullability/posttakesnullablereturnsnullable"]) {
            assertNotNull(this)
            val post = this.post
            assertNotNull(post)
            val postReqBody = post.requestBody
            assertTrue(postReqBody.required)
            val requestBodyJson = post.requestBody.content["application/json"]
            assertNotNull(requestBodyJson)
            val ref = requestBodyJson.schema.`$ref`
            assertThat(ref).isEqualTo("#/components/schemas/SomeInfo")
            val successResponse = post.responses["200"]
            assertNotNull(successResponse)
            val content = successResponse.content["application/json"]
            assertNotNull(content)
            val schema = content.schema
            assertTrue(schema.nullable, "The schema should have the nullable property")
        }

        with(openAPI.components.schemas["SomeInfo"]) {
            assertNotNull(this)
            assertNull(this.nullable)
            val idProperty = this.properties["id"]
            assertNotNull(idProperty)
            assertThat(idProperty.nullable).isFalse
            val numberProperty = this.properties["number"]
            assertNotNull(numberProperty)
            assertThat(numberProperty.nullable).isFalse
            assertThat(this.required.toSet()).isEqualTo(setOf("id", "number"))
        }

        with(openAPI.paths["/nullability/posttakesrequiredstringreturnsnullablestring"]) {
            assertNotNull(this)
            val post = this.post
            assertNotNull(post)
            val postReqBody = post.requestBody
            assertTrue(postReqBody.required)
            val requestBodyJson = post.requestBody.content["application/json"]
            assertNotNull(requestBodyJson)
            val schema = requestBodyJson.schema

            val requiredString = assertNotNull(schema.properties["requiredString1"])
            assertThat(requiredString.nullable).isFalse()
            assertThat(requiredString.type).isEqualTo("string")

            assertThat(schema.required).isEqualTo(listOf("requiredString1"))
        }
    }
}
