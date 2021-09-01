package net.corda.httprpc.tools.annotations.validation

import net.corda.v5.application.messaging.RPCOps
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.api.annotations.HttpRpcPathParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcQueryParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParameterClassTypeValidatorTest {
    @Test
    fun `validate withInvalidParamClassTypes errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RPCOps {
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
        class TestInterface : RPCOps {
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