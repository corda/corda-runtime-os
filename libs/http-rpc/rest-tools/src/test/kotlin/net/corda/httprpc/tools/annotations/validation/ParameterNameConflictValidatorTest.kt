package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParameterNameConflictValidatorTest {
    @Test
    fun `validate withSameParamNames errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            fun test(@HttpRpcQueryParameter(name = "foo") foo1: String, @HttpRpcQueryParameter(name = "foo") foo2: String) {
                foo1.lowercase()
                foo2.lowercase()
            }
        }

        val result = ParameterNameConflictValidator(TestInterface::class.java)
            .validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withSameParamNamesInDefault errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            fun test(@HttpRpcQueryParameter(name = "foo") foo1: String, @HttpRpcQueryParameter foo: String) {
                foo1.lowercase()
                foo.lowercase()
            }
        }

        val result = ParameterNameConflictValidator(TestInterface::class.java)
            .validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withSameParamNamesWithCapitalization errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            fun test(
                @HttpRpcQueryParameter(name = "foo") foo1: String,
                @HttpRpcQueryParameter Foo: String,
                @HttpRpcQueryParameter(name = "FOO") foo2: String
            ) {
                Foo.lowercase()
                foo1.lowercase()
                foo2.lowercase()
            }
        }

        val result = ParameterNameConflictValidator(TestInterface::class.java)
            .validate()

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validate withSameParamNamesInDifferentTypes errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            fun test(@HttpRpcQueryParameter(name = "foo") foo1: String, @HttpRpcPathParameter(name = "foo") foo2: String) {
                foo1.lowercase()
                foo2.lowercase()
            }
        }

        val result = ParameterNameConflictValidator(TestInterface::class.java)
            .validate()

        assertEquals(0, result.errors.size)
    }

    @Test
    fun `validate withSameParamNamesInBody errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            fun test(foO: String, @HttpRpcRequestBodyParameter(name = "Foo") foo2: String) {
                foO.lowercase()
                foo2.lowercase()
            }
        }

        val result = ParameterNameConflictValidator(TestInterface::class.java)
            .validate()

        assertEquals(1, result.errors.size)
    }
}
