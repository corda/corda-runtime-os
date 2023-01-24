package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.annotations.RestRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParameterAnnotationValidatorTest {
    @Test
    fun `validate withInvalidAnnotation errorListContainsError`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            fun test(foo: String, @RestQueryParameter @RestRequestBodyParameter bar: String) {
                foo.lowercase()
                bar.lowercase()
            }
        }

        val result = ParameterAnnotationValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withNoAnnotation errorListIsEmpty`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            fun test(foo: String, bar: String) {
                foo.lowercase()
                bar.lowercase()
            }
        }

        val result = ParameterAnnotationValidator(TestInterface::class.java).validate()

        assert(result.errors.isEmpty())
    }
}
