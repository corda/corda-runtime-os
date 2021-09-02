package net.corda.v5.httprpc.tools.annotations.validation

import net.corda.v5.application.messaging.RPCOps
import net.corda.v5.httprpc.api.annotations.HttpRpcGET
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.api.annotations.HttpRpcRequestBodyParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParameterBodyAnnotationValidatorTest {
    @Test
    fun `validate withInvalidBodyAnnotation errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RPCOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            fun test(@HttpRpcRequestBodyParameter foo: String) {
                foo.toLowerCase()
            }

            @HttpRpcGET
            fun testWithImplicitBodyParam(foo: String) {
                foo.toLowerCase()
            }
        }

        val result = ParameterBodyAnnotationValidator(TestInterface::class.java)
            .validate()

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validate withManyBodyParametersAnnotations errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RPCOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(@HttpRpcRequestBodyParameter foo: String, @HttpRpcRequestBodyParameter bar: String) {
                foo.toLowerCase()
                bar.toLowerCase()
            }

            @HttpRpcPOST
            fun testWithImplicitBodyParam(foo: String, bar: String) {
                foo.toLowerCase()
                bar.toLowerCase()
            }
        }

        val result = ParameterBodyAnnotationValidator(TestInterface::class.java)
            .validate()

        assert(result.errors.isEmpty())
    }
}