package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParameterAnnotationValidatorTest {
    @Test
    fun `validate withInvalidAnnotation errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(foo: String, @HttpRpcQueryParameter @HttpRpcRequestBodyParameter bar: String) {
                foo.lowercase()
                bar.lowercase()
            }
        }

        val result = ParameterAnnotationValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withNoAnnotation errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(foo: String, bar: String) {
                foo.lowercase()
                bar.lowercase()
            }
        }

        val result = ParameterAnnotationValidator(TestInterface::class.java).validate()

        assert(result.errors.isEmpty())
    }
}
