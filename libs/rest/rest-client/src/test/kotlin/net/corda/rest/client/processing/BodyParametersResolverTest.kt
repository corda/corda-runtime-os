package net.corda.rest.client.processing

import net.corda.rest.test.TestHealthCheckAPI
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertEquals

class BodyParametersResolverTest {
    @Test
    fun `bodyParametersResolver_withBodyParametersMethod_returnsSerializedString`() {
        val result = TestHealthCheckAPI::ping.javaMethod!!.bodyParametersFrom(arrayOf(TestHealthCheckAPI.PingPongData("test")))

        assertEquals("""{"pingPongData":{"str":"test"}}""", result)
    }

    @Test
    fun `bodyParametersResolver_withBodyParametersMethodWithNullBody_returnsSerializedString`() {
        val result = TestHealthCheckAPI::ping.javaMethod!!.bodyParametersFrom(arrayOf(null))

        assertEquals("""{"pingPongData":null}""", result)
    }

    @Test
    fun `bodyParametersResolver_withBodyAndExtraParameters_returnsSerializedValues`() {
        val result = TestHealthCheckAPI::ping.javaMethod!!.bodyParametersFrom(arrayOf(null), mapOf("field" to 1))

        assertEquals("""{"pingPongData":null,"field":1}""", result)
    }

    @Test
    fun `bodyParametersResolver_withoutBodyParametersMethod_returnsEmptyString`() {
        val result = TestHealthCheckAPI::hello2.javaMethod!!.bodyParametersFrom(arrayOf(null, null))

        assertEquals("{}", result)
    }
}