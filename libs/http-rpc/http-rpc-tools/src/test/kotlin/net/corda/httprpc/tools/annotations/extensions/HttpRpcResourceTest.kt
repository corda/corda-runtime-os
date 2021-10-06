package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HttpRpcResourceTest {

    @Test
    fun `path from class name will be converted to lowercase`() {
        @HttpRpcResource
        class TestClass

        val resourcePath = TestClass::class.java.let { it.getAnnotation(HttpRpcResource::class.java).path(it) }
        assertEquals("testclass", resourcePath)
    }

    @Test
    fun `path from HttpRpcResource annotation will be converted to lowercase`() {
        @HttpRpcResource(
            path = "TestClassPath/"
        )
        class TestClass

        val resourcePath = TestClass::class.java.let { it.getAnnotation(HttpRpcResource::class.java).path(it) }
        assertEquals("testclasspath/", resourcePath)
    }
}