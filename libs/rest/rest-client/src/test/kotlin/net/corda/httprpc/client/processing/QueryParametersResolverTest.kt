package net.corda.httprpc.client.processing

import net.corda.httprpc.test.TestHealthCheckAPI
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryParametersResolverTest {
    @Test
    fun `queryParametersResolver_withQueryParametersMethod_returnsPopulatedMap`() {
        val result = TestHealthCheckAPI::hello2.javaMethod!!.queryParametersFrom(arrayOf("test 1", "test 2"))

        // "test+1" is a URL encoded version of "test 1". "test 2" value is ignored in this case as it is listed as path parameter
        // in net.corda.rest.test.TestHealthCheckAPI::hello2
        assertEquals("test+1", result["id"])
    }

    @Test
    fun `queryParametersResolver_withQueryParametersMethodAndNullQueryParam_returnsPopulatedMap`() {
        val result = TestHealthCheckAPI::hello2.javaMethod!!.queryParametersFrom(arrayOf(null, null))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `queryParametersResolver_withoutQueryParametersMethod_returnsEmptyMap`() {
        val result = TestHealthCheckAPI::ping.javaMethod!!.queryParametersFrom(arrayOf(TestHealthCheckAPI.PingPongData("data")))

        assertEquals(0, result.size)
    }
}