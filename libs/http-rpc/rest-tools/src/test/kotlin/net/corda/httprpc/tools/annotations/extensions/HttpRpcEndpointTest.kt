package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaMethod

class HttpRpcEndpointTest {

    @Test
    fun `path from GET method name will be converted to lowercase`() {

        abstract class TestClass {
            @HttpRpcGET
            abstract fun sampleGetMethod(): String
        }

        val endpointPath = TestClass::sampleGetMethod.let { it.findAnnotation<HttpRpcGET>()!!.path(it.javaMethod!!) }

        assertNull(endpointPath)
    }

    @Test
    fun `path from HttpRpcGET annotation will be converted to lowercase`() {

        abstract class TestClass {
            @HttpRpcGET(
                path = "SampleGetMethodPath",
            )
            abstract fun sampleGetMethod(): String
        }

        val endpointPath = TestClass::sampleGetMethod.let { it.findAnnotation<HttpRpcGET>()!!.path(it.javaMethod!!) }

        assertEquals("samplegetmethodpath", endpointPath)
    }

    @Test
    fun `path from POST method name will be converted to lowercase`() {

        abstract class TestClass {
            @HttpRpcPOST
            abstract fun samplePostMethod(): String
        }

        val endpointPath = TestClass::samplePostMethod.let { it.findAnnotation<HttpRpcPOST>()!!.path() }

        assertNull(endpointPath)
    }

    @Test
    fun `path from HttpRpcPOST annotation will be converted to lowercase`() {

        abstract class TestClass {
            @HttpRpcPOST(
                path = "samplePostMethodPath",
            )
            abstract fun samplePostMethod(): String
        }

        val endpointPath = TestClass::samplePostMethod.let { it.findAnnotation<HttpRpcPOST>()!!.path() }

        assertEquals("samplepostmethodpath", endpointPath)
    }
}