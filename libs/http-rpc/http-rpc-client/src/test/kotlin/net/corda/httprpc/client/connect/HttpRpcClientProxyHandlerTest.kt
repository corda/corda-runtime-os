package net.corda.httprpc.client.connect

import net.corda.httprpc.client.config.EmptyAuthenticationConfig
import net.corda.httprpc.client.connect.remote.RemoteUnirestClient
import net.corda.httprpc.client.connect.stream.HttpRpcFiniteDurableCursorClientBuilderImpl
import net.corda.httprpc.client.processing.WebRequest
import net.corda.httprpc.client.processing.WebResponse
import net.corda.httprpc.client.processing.encodeParam
import net.corda.httprpc.test.CalendarRestResource
import net.corda.httprpc.test.CustomSerializationAPI
import net.corda.httprpc.test.CustomString
import net.corda.httprpc.test.TestHealthCheckAPI
import net.corda.httprpc.tools.HttpVerb
import org.apache.commons.lang3.reflect.TypeUtils
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertNull

internal class HttpRpcClientProxyHandlerTest {
    private val client = mock<RemoteUnirestClient>().also {
        doReturn(WebResponse<Any>(null, emptyMap(), 200, null))
            .whenever(it).call<Any>(any(), any(), any())
        doReturn(WebResponse<Any>(null, emptyMap(), 200, null))
            .whenever(it).call<Any>(any(), any(), any(), any())
    }

    private val testHealthCheckApiProxyHandler = HttpRpcClientProxyHandler(
        client = client, EmptyAuthenticationConfig, TestHealthCheckAPI::class.java
    )

    private val customSerializationApiProxyHandler = HttpRpcClientProxyHandler(
        client = client, EmptyAuthenticationConfig, CustomSerializationAPI::class.java
    )

    @Test
    fun `should call client without expecting response if response type is void`() {
        val result = testHealthCheckApiProxyHandler.invoke(Any(), TestHealthCheckAPI::voidResponse.javaMethod!!, null)

        verify(client, times(1)).call<Any>(
            eq(HttpVerb.GET), argThat { this.path == "health/void" }, any()
        )
        assertNull(result)
    }

    @Test
    fun `should call client expecting response if response type is not void`() {
        val result = testHealthCheckApiProxyHandler.invoke(Any(), TestHealthCheckAPI::void.javaMethod!!, null)

        verify(client, times(1)).call<Any>(
            eq(HttpVerb.GET),
            argThat { this.path == "health/sanity" },
            any()
        )
        assertNull(result)
    }

    @Test
    fun `should call client translating path and query parameters if they exist`() {
        val result = testHealthCheckApiProxyHandler.invoke(Any(), TestHealthCheckAPI::hello2.javaMethod!!, arrayOf("string1", "string2"))

        verify(client, times(1)).call<Any>(
            eq(HttpVerb.GET),
            argThat { this.queryParameters!!["id"] == "string1" && this.path == "health/hello2/string2" },
            any()
        )
        assertNull(result)
    }

    @Test
    fun `should call client translating body on post call if it exists`() {
        val result = testHealthCheckApiProxyHandler.invoke(
            Any(), TestHealthCheckAPI::ping.javaMethod!!, arrayOf(TestHealthCheckAPI.PingPongData("example"))
        )

        verify(client, times(1)).call<Any>(
            eq(HttpVerb.POST),
            argThat { this.body == """{"pingPongData":{"str":"example"}}""" },
            any()
        )
        assertNull(result)
    }

    @Test
    fun `should call client translating body on post call if it is null`() {
        val result = testHealthCheckApiProxyHandler.invoke(Any(), TestHealthCheckAPI::ping.javaMethod!!, arrayOf(null))

        verify(client, times(1)).call<Any>(
            eq(HttpVerb.POST),
            argThat { this.body == """{"pingPongData":null}""" },
            any()
        )
        assertNull(result)
    }

    @Test
    fun `should not call client and return querycursorbuilder when the call is a durablestream call`() {
        val result = testHealthCheckApiProxyHandler.invoke(Any(), CalendarRestResource::daysOfTheYear.javaMethod!!, arrayOf(2020))

        verify(client, times(0)).call<Any>(
            any(),
            any(),
            any(),
            any()
        )
        assert(result is HttpRpcFiniteDurableCursorClientBuilderImpl)
    }

    @Test
    fun `should call client translating query list and response type if it is list`() {
        val elements = listOf("1.0", "2.0")
        val result = testHealthCheckApiProxyHandler.invoke(Any(), TestHealthCheckAPI::plusOne.javaMethod!!, arrayOf(elements))

        verify(client, times(1)).call(
            eq(HttpVerb.GET),
            argThat<WebRequest<String>> {
                val transformedArgs = elements.map { it.encodeParam() }
                this.queryParameters!!["numbers"] == transformedArgs
            },
            eq(TypeUtils.parameterize(List::class.java, java.lang.Double::class.java)),
            any()
        )
        assertNull(result)
    }

    @Test
    fun `should call client translating body with custom serializer successfully`() {
        val result = customSerializationApiProxyHandler.invoke(
            Any(), CustomSerializationAPI::printString.javaMethod!!, arrayOf(CustomString("test"))
        )

        verify(client, times(1)).call(
            eq(HttpVerb.POST),
            argThat<WebRequest<String>> { this.body == """{"s":"custom test"}""" },
            eq(CustomString::class.java),
            any()
        )
        assertNull(result)
    }

    @Test
    fun `should refuse to call client if the rpc since version is higher than the handler`() {
        testHealthCheckApiProxyHandler.setServerProtocolVersion(1)

        assertThatThrownBy { testHealthCheckApiProxyHandler.invoke(Any(), TestHealthCheckAPI::laterAddedCall.javaMethod!!, null) }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }
}