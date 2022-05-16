package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParameterBodyAnnotationValidatorTest {
    @Test
    fun `validate withInvalidBodyAnnotation errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            fun test(@HttpRpcRequestBodyParameter foo: String) {
                foo.lowercase()
            }

            @HttpRpcGET
            fun testWithImplicitBodyParam(foo: String) {
                foo.lowercase()
            }
        }

        val result = ParameterBodyAnnotationValidator(TestInterface::class.java)
            .validate()

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validate withManyBodyParametersAnnotations errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(@HttpRpcRequestBodyParameter foo: String, @HttpRpcRequestBodyParameter bar: String) {
                foo.lowercase()
                bar.lowercase()
            }

            @HttpRpcPOST
            fun testWithImplicitBodyParam(foo: String, bar: String) {
                foo.lowercase()
                bar.lowercase()
            }
        }

        val result = ParameterBodyAnnotationValidator(TestInterface::class.java)
            .validate()

        assert(result.errors.isEmpty())
    }
}
