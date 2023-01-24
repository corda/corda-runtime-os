package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParameterClassTypeValidatorTest {
    @Test
    fun `validate withInvalidParamClassTypes errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(@HttpRpcPathParameter bar: List<String>, @HttpRpcQueryParameter foo: List<String>) {
                bar.isNotEmpty()
                foo.isNotEmpty()
            }
        }

        val result = ParameterClassTypeValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withValidParamClassTypes errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(@HttpRpcPathParameter bar: Boolean, @HttpRpcQueryParameter foo: Double) {
                !bar
                foo + 1.0
            }
        }

        val result = ParameterClassTypeValidator(TestInterface::class.java).validate()

        assertEquals(0, result.errors.size)
    }
}