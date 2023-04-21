package net.corda.rest.server.impl.apigen.processing

import net.corda.rest.server.impl.apigen.models.Endpoint
import net.corda.rest.server.impl.apigen.models.EndpointMethod
import net.corda.rest.server.impl.apigen.models.InvocationMethod
import net.corda.rest.server.impl.apigen.models.ResponseBody
import net.corda.rest.test.TestHealthCheckAPI
import net.corda.rest.test.TestHealthCheckAPIImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

class RouteInfoTest {

    @Test
    fun `endpointInvocation withoutParameters shouldSucceed`() {
        val endpoint = Endpoint(
            invocationMethod = InvocationMethod(TestHealthCheckAPI::void.javaMethod!!, TestHealthCheckAPIImpl()),
            description = "Sanity endpoint",
            method = EndpointMethod.GET,
            parameters = emptyList(),
            path = "sanity",
            responseBody = ResponseBody(description = "", type = String::class.java, parameterizedTypes = emptyList()),
            title = "Sanity"
        )
        val route = RouteInfo("sanity", "", "", endpoint)
        assertEquals("Sane", route.invokeDelegatedMethod())
    }

    @Test
    fun `endpointInvocation withParameters shouldSucceed`() {
        val endpoint = Endpoint(
            invocationMethod = InvocationMethod(TestHealthCheckAPI::hello.javaMethod!!, TestHealthCheckAPIImpl()),
            description = "Hello endpoint",
            method = EndpointMethod.GET,
            parameters = emptyList(),
            path = "hello/{name}",
            responseBody = ResponseBody(description = "", type = String::class.java, parameterizedTypes = emptyList()),
            title = "Hello"
        )
        val param1 = "name"
        val param2 = 1
        val route = RouteInfo("", "", "", endpoint)
        assertEquals("Hello 1 : name", route.invokeDelegatedMethod(param1, param2))
    }

    @Test
    fun `endpointInvocation withParametersList shouldSucceed`() {
        val endpoint = Endpoint(
            invocationMethod = InvocationMethod(TestHealthCheckAPI::plusOne.javaMethod!!, TestHealthCheckAPIImpl()),
            description = "Sanity endpoint",
            method = EndpointMethod.GET,
            parameters = emptyList(),
            path = "sanity",
            responseBody = ResponseBody(description = "Increased by one", type = String::class.java, parameterizedTypes = emptyList()),
            title = "Sanity"
        )
        val params = listOf("1", "2", "3")
        val route = RouteInfo("", "", "", endpoint)
        assertEquals(listOf(2.0, 3.0, 4.0), route.invokeDelegatedMethod(params))
    }
}