package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.HttpRestResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParameterClassTypeValidatorTest {
    @Test
    fun `validate withInvalidParamClassTypes errorListContainsError`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            fun test(@RestPathParameter bar: List<String>, @RestQueryParameter foo: List<String>) {
                bar.isNotEmpty()
                foo.isNotEmpty()
            }
        }

        val result = ParameterClassTypeValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withValidParamClassTypes errorListIsEmpty`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            fun test(@RestPathParameter bar: Boolean, @RestQueryParameter foo: Double) {
                !bar
                foo + 1.0
            }
        }

        val result = ParameterClassTypeValidator(TestInterface::class.java).validate()

        assertEquals(0, result.errors.size)
    }
}