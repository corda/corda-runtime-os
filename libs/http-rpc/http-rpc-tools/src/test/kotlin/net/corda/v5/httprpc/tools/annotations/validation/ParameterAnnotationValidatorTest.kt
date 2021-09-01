package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.tools.annotations.validation.ParameterAnnotationValidator
import net.corda.v5.application.messaging.RPCOps
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.api.annotations.HttpRpcQueryParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcRequestBodyParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParameterAnnotationValidatorTest {
    @Test
    fun `validate withInvalidAnnotation errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RPCOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(foo: String, @HttpRpcQueryParameter @HttpRpcRequestBodyParameter bar: String) {
                foo.toLowerCase()
                bar.toLowerCase()
            }
        }

        val result = ParameterAnnotationValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withNoAnnotation errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RPCOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(foo: String, bar: String) {
                foo.toLowerCase()
                bar.toLowerCase()
            }
        }

        val result = ParameterAnnotationValidator(TestInterface::class.java).validate()

        assert(result.errors.isEmpty())
    }
}