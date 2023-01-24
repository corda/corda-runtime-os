package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathParameterInURLPathValidatorTest {
    @Test
    fun `validate withPathParamWithCustomNameExisting errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET(path = "abc/{foo}/def")
            fun test(@HttpRpcPathParameter(name = "foo") foo2: String) {
                foo2.lowercase()
            }
        }

        val result = PathParameterInURLPathValidator(TestInterface::class.java).validate()

        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validate withPathParamWithDefaultNameExisting errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET(path = "abc/{foo2}/def")
            fun test(@HttpRpcPathParameter foo2: String) {
                foo2.lowercase()
            }
        }

        val result = PathParameterInURLPathValidator(TestInterface::class.java).validate()

        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validate withPathParamNotExisting errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET(path = "abc/{param}/def")
            fun test(@HttpRpcPathParameter foo2: String) {
                foo2.lowercase()
            }
        }

        val result = PathParameterInURLPathValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withMultiplePathParamsNotExisting errorListContainsAllErrors`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET(path = "abc/{param}/def")
            fun test(@HttpRpcPathParameter foo2: String, @HttpRpcPathParameter foo1: String) {
                foo1.lowercase()
                foo2.lowercase()
            }
        }

        val result = PathParameterInURLPathValidator(TestInterface::class.java).validate()

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validate withPathParamWithDifferentCaseExisting errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET(path = "abc/{FOO2}/def")
            fun test(@HttpRpcPathParameter foO2: String) {
                foO2.lowercase()
            }
        }

        val result = PathParameterInURLPathValidator(TestInterface::class.java).validate()

        assertTrue(result.errors.isEmpty())
    }
}
