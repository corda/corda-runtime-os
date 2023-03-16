package net.corda.rest.server.impl.apigen.processing

import net.corda.rest.server.impl.apigen.models.Endpoint
import net.corda.rest.server.impl.apigen.models.EndpointMethod
import net.corda.rest.server.impl.apigen.models.InvocationMethod
import net.corda.rest.server.impl.apigen.models.Resource
import net.corda.rest.server.impl.apigen.models.ResponseBody
import net.corda.rest.RestResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod

class JavalinRouteProviderImplTest {
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
        val apiVersion = "1"
        val resourceName = "testresource"
        val resourcePath = "testpath"
        val testEndpoint = Endpoint(
                EndpointMethod.GET, "", "",
            "abc/${pathParameterStartMarker}${testEndpointName}${pathParameterEndMarker}/def",
            emptyList(), ResponseBody("", Unit::class.java), invocationMethod
        )

        val testResource = Resource(resourceName, "", resourcePath, setOf(testEndpoint))
        val provider = JavalinRouteProviderImpl(basePath, apiVersion, listOf(testResource))
        val expectedPath = ("/${basePath}/v${apiVersion}/${testResource.path}/abc/" +
                "${pathParameterStartMarker}${testEndpointName}${pathParameterEndMarker}/def").lowercase()

        val result = provider.httpGetRoutes
        assertEquals(expectedPath, result.single().fullPath)
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
        val apiVersion = "1"
        val resourceName = "testresource"
        val resourcePath = "testpath"
        val testEndpoint = Endpoint(
                EndpointMethod.GET, "", "",
            "getprotocolversion",
            emptyList(), ResponseBody("", Unit::class.java), invocationMethod
        )

        val testResource = Resource(resourceName, "", resourcePath, setOf(testEndpoint))
        val provider = JavalinRouteProviderImpl(basePath, apiVersion, listOf(testResource))
        val expectedPath = "/${basePath}/v${apiVersion}/${testResource.path}/${testEndpointName}".lowercase()

        val getRoutes = provider.httpGetRoutes
        val noAuthRequiredGetRoutes = provider.httpNoAuthRequiredGetRoutes

        assertEquals(0, getRoutes.size)
        assertEquals(1, noAuthRequiredGetRoutes.size)
        assertEquals(expectedPath, noAuthRequiredGetRoutes.single().fullPath)
    }
}
