package net.corda.rest.server.impl.apigen.processing

import net.corda.rest.RestResource
import net.corda.rest.SC_OK
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.server.impl.apigen.models.Endpoint
import net.corda.rest.server.impl.apigen.models.EndpointMethod
import net.corda.rest.server.impl.apigen.models.InvocationMethod
import net.corda.rest.server.impl.apigen.models.Resource
import net.corda.rest.server.impl.apigen.models.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod

class JavalinRouteProviderImplTest {
    private companion object {
        val API_VERSIONS = setOf(RestApiVersion.C5_0, RestApiVersion.C5_1)
    }

    @Test
    fun `httpGetRoutes withPathParameter parameterTranslatedSuccessfully`() {
        class TestInterface : RestResource {
            fun foo() {}
            override val protocolVersion: Int
                get() = 1
        }

        val invocationMethod = InvocationMethod(TestInterface::foo.javaMethod!!, TestInterface())
        val testEndpointName = "foo"
        val pathParameterStartMarker = "{"
        val pathParameterEndMarker = "}"
        val basePath = "testBase"
        val resourceName = "testresource"
        val resourcePath = "testpath"
        val testEndpoint = Endpoint(
            EndpointMethod.GET,
            "",
            "",
            "abc/${pathParameterStartMarker}${testEndpointName}$pathParameterEndMarker/def",
            emptyList(),
            ResponseBody("", SC_OK, Unit::class.java),
            invocationMethod,
            API_VERSIONS
        )

        val testResource = Resource(resourceName, "", resourcePath, setOf(testEndpoint), API_VERSIONS)
        val provider = JavalinRouteProviderImpl(basePath, listOf(testResource))
        val expectedPaths = API_VERSIONS.map { apiVersion ->
            (
                "/$basePath/${apiVersion.versionPath}/${testResource.path}/abc/" +
                    "${pathParameterStartMarker}${testEndpointName}$pathParameterEndMarker/def"
                ).lowercase()
        }.toSet()

        val result = provider.httpGetRoutes
        assertThat(result.map { it.fullPath }.toSet()).isEqualTo(expectedPaths)
    }

    @Test
    fun `httpGetRoutes NoAuthRequired routeInfoReturnedSuccessfully`() {
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        val invocationMethod = InvocationMethod(TestInterface::protocolVersion.javaGetter!!, TestInterface())
        val testEndpointName = "getprotocolversion"
        val basePath = "testBase"
        val resourceName = "testresource"
        val resourcePath = "testpath"
        val testEndpoint = Endpoint(
            EndpointMethod.GET,
            "",
            "",
            "getprotocolversion",
            emptyList(),
            ResponseBody("", SC_OK, Unit::class.java),
            invocationMethod,
            API_VERSIONS
        )

        val testResource = Resource(resourceName, "", resourcePath, setOf(testEndpoint), API_VERSIONS)
        val provider = JavalinRouteProviderImpl(basePath, listOf(testResource))
        val expectedPaths =
            API_VERSIONS.map { apiVersion -> "/$basePath/${apiVersion.versionPath}/${testResource.path}/$testEndpointName".lowercase() }
                .toSet()

        val getRoutes = provider.httpGetRoutes
        val noAuthRequiredGetRoutes = provider.httpNoAuthRequiredGetRoutes

        assertEquals(0, getRoutes.size)
        assertEquals(2, noAuthRequiredGetRoutes.size)
        assertThat(noAuthRequiredGetRoutes.map { it.fullPath }.toSet()).isEqualTo(expectedPaths)
    }
}
