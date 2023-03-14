package net.corda.rest.client.processing

import net.corda.rest.test.TestHealthCheckAPI
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertEquals

class PathParametersResolverTest {
    @Test
    fun `pathParametersResolver_withPathParametersMethod_returnsPopulatedMap`() {
        val result = TestHealthCheckAPI::hello2.javaMethod!!.pathParametersFrom(arrayOf(null, "test2"))

        assertEquals("test2", result["name"])
    }

    @Test
    fun `pathParametersResolver_withPathParametersMethodAndNullPathParam_returnsPopulatedMap`() {
        val result = TestHealthCheckAPI::hello2.javaMethod!!.pathParametersFrom(arrayOf(null, null))

        assertEquals("null", result["name"])
    }

    @Test
    fun `pathParametersResolver_withoutPathParametersMethod_returnsEmptyMap`() {
        val result = TestHealthCheckAPI::ping.javaMethod!!.pathParametersFrom(arrayOf(TestHealthCheckAPI.PingPongData("data")))

        assertEquals(0, result.size)
    }
}