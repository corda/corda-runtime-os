package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.v5.base.annotations.CordaSerializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

class ParameterBodyCordaSerializableAnnotationValidatorTest {
    @Test
    fun `validate withNoCordaSerializableAnnotation errorListContainsError`() {
        data class CustomString(val s: String)

        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(@HttpRpcRequestBodyParameter foo: CustomString) {
                foo.s.toLowerCase()
            }
        }

        val result = ParameterBodyCordaSerializableAnnotationValidator(TestInterface::class.java)
            .validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withNoCordaSerializableAnnotationAndDefaultBodyParameter errorListContainsError`() {
        data class CustomString(val s: String)

        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(foo: CustomString) {
                foo.s.toLowerCase()
            }
        }

        val result = ParameterBodyCordaSerializableAnnotationValidator(TestInterface::class.java)
            .validate()

        assertEquals(1, result.errors.size)
        assertEquals(
            ParameterBodyCordaSerializableAnnotationValidator.error(TestInterface::test.javaMethod!!, "foo"),
            result.errors.single()
        )
    }

    @Test
    fun `validate withWrapperParam errorListContainsNoError`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(@HttpRpcRequestBodyParameter foo: String) {
                foo.toLowerCase()
            }

            @HttpRpcPOST
            fun testWithImplicitBodyParam(foo: String) {
                foo.toLowerCase()
            }
        }

        val result = ParameterBodyCordaSerializableAnnotationValidator(TestInterface::class.java)
            .validate()

        assert(result.errors.isEmpty())
    }

    @Test
    fun `validate withCordaSerializableParam errorListContainsNoError`() {
        @CordaSerializable
        data class CustomString(val s: String)

        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(@HttpRpcRequestBodyParameter foo: CustomString) {
                foo.s.toLowerCase()
            }

            @HttpRpcPOST
            fun testWithImplicitBodyParam(foo: CustomString) {
                foo.s.toLowerCase()
            }
        }

        val result = ParameterBodyCordaSerializableAnnotationValidator(TestInterface::class.java)
            .validate()

        assert(result.errors.isEmpty())
    }
}