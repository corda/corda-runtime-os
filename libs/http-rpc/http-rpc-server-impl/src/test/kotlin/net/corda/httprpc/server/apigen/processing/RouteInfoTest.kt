package net.corda.httprpc.server.apigen.processing

import net.corda.httprpc.server.apigen.models.Endpoint
import net.corda.httprpc.server.apigen.models.InvocationMethod
import net.corda.httprpc.server.apigen.test.TestHealthCheckAPI
import net.corda.httprpc.server.apigen.test.TestHealthCheckAPIImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.reflect.jvm.javaMethod

class RouteInfoTest {
    @Test
    fun `endpointInvocation withoutParameters shouldSucceed`() {
        val endpointMock = mock<Endpoint> {
            on { invocationMethod } doReturn InvocationMethod(TestHealthCheckAPI::void.javaMethod!!, TestHealthCheckAPIImpl())
        }

        val route = RouteInfo("", "", "", endpointMock)

        assertEquals("Sane", route.invokeDelegatedMethod())
    }

    @Test
    fun `endpointInvocation withParameters shouldSucceed`() {
        val endpointMock = mock<Endpoint> {
            on { invocationMethod } doReturn InvocationMethod(TestHealthCheckAPI::hello.javaMethod!!, TestHealthCheckAPIImpl())
        }
        val param1 = "name"
        val param2 = 1

        val route = RouteInfo("", "", "", endpointMock)

        assertEquals("Hello 1 : name", route.invokeDelegatedMethod(param1, param2))
    }

    @Test
    fun `endpointInvocation withParametersList shouldSucceed`() {
        val endpointMock = mock<Endpoint> {
            on { invocationMethod } doReturn InvocationMethod(TestHealthCheckAPI::plusOne.javaMethod!!, TestHealthCheckAPIImpl())
        }
        val params = listOf("1", "2", "3")

        val route = RouteInfo("", "", "", endpointMock)

        assertEquals(listOf(2.0, 3.0, 4.0), route.invokeDelegatedMethod(params))
    }
}