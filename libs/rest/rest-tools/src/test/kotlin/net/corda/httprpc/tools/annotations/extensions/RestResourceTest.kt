package net.corda.rest.tools.annotations.extensions

import net.corda.rest.annotations.HttpRestResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RestResourceTest {

    @Test
    fun `path from class name will be converted to lowercase`() {
        @HttpRestResource
        class TestClass

        val resourcePath = TestClass::class.java.let { it.getAnnotation(HttpRestResource::class.java).path(it) }
        assertEquals("testclass", resourcePath)
    }

    @Test
    fun `path from HttpRpcResource annotation will be converted to lowercase`() {
        @HttpRestResource(
            path = "TestClassPath/"
        )
        class TestClass

        val resourcePath = TestClass::class.java.let { it.getAnnotation(HttpRestResource::class.java).path(it) }
        assertEquals("testclasspath/", resourcePath)
    }
}