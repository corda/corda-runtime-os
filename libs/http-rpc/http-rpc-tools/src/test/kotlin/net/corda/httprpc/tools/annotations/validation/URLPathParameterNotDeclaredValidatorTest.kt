package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class URLPathParameterNotDeclaredValidatorTest {
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

        val result = URLPathParameterNotDeclaredValidator(TestInterface::class.java).validate()

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

        val result = URLPathParameterNotDeclaredValidator(TestInterface::class.java).validate()

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

        val result = URLPathParameterNotDeclaredValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withMultiplePathParamsNotExisting errorListContainsAllErrors`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET(path = "abc/{param}/{param2}/def")
            fun test() {
            }
        }

        val result = URLPathParameterNotDeclaredValidator(TestInterface::class.java).validate()

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validate withPathParamsWithDifferentCase errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET(path = "abc/{FOO2}/def")
            fun test(@HttpRpcPathParameter foo2: String) {
                foo2.lowercase()
            }
        }

        val result = URLPathParameterNotDeclaredValidator(TestInterface::class.java).validate()

        assertTrue(result.errors.isEmpty())
    }
}
