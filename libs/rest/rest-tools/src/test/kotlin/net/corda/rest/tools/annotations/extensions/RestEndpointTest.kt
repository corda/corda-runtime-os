package net.corda.rest.tools.annotations.extensions

import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaMethod

class RestEndpointTest {

    @Test
    fun `path from GET method name will be converted to lowercase`() {

        abstract class TestClass {
            @HttpGET
            abstract fun sampleGetMethod(): String
        }

        val endpointPath = TestClass::sampleGetMethod.let { it.findAnnotation<HttpGET>()!!.path(it.javaMethod!!) }

        assertNull(endpointPath)
    }

    @Test
    fun `path from HttpGET annotation will be converted to lowercase`() {

        abstract class TestClass {
            @HttpGET(
                path = "SampleGetMethodPath",
            )
            abstract fun sampleGetMethod(): String
        }

        val endpointPath = TestClass::sampleGetMethod.let { it.findAnnotation<HttpGET>()!!.path(it.javaMethod!!) }

        assertEquals("samplegetmethodpath", endpointPath)
    }

    @Test
    fun `path from POST method name will be converted to lowercase`() {

        abstract class TestClass {
            @HttpPOST
            abstract fun samplePostMethod(): String
        }

        val endpointPath = TestClass::samplePostMethod.let { it.findAnnotation<HttpPOST>()!!.path() }

        assertNull(endpointPath)
    }

    @Test
    fun `path from HttpPOST annotation will be converted to lowercase`() {

        abstract class TestClass {
            @HttpPOST(
                path = "samplePostMethodPath",
            )
            abstract fun samplePostMethod(): String
        }

        val endpointPath = TestClass::samplePostMethod.let { it.findAnnotation<HttpPOST>()!!.path() }

        assertEquals("samplepostmethodpath", endpointPath)
    }
}