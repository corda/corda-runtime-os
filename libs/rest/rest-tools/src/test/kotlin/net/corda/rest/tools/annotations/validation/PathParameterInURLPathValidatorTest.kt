package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.HttpRestResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathParameterInURLPathValidatorTest {
    @Test
    fun `validate withPathParamWithCustomNameExisting errorListIsEmpty`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET(path = "abc/{foo}/def")
            fun test(@RestPathParameter(name = "foo") foo2: String) {
                foo2.lowercase()
            }
        }

        val result = PathParameterInURLPathValidator(TestInterface::class.java).validate()

        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validate withPathParamWithDefaultNameExisting errorListIsEmpty`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET(path = "abc/{foo2}/def")
            fun test(@RestPathParameter foo2: String) {
                foo2.lowercase()
            }
        }

        val result = PathParameterInURLPathValidator(TestInterface::class.java).validate()

        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validate withPathParamNotExisting errorListContainsError`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET(path = "abc/{param}/def")
            fun test(@RestPathParameter foo2: String) {
                foo2.lowercase()
            }
        }

        val result = PathParameterInURLPathValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withMultiplePathParamsNotExisting errorListContainsAllErrors`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET(path = "abc/{param}/def")
            fun test(@RestPathParameter foo2: String, @RestPathParameter foo1: String) {
                foo1.lowercase()
                foo2.lowercase()
            }
        }

        val result = PathParameterInURLPathValidator(TestInterface::class.java).validate()

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validate withPathParamWithDifferentCaseExisting errorListIsEmpty`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET(path = "abc/{FOO2}/def")
            fun test(@RestPathParameter foO2: String) {
                foO2.lowercase()
            }
        }

        val result = PathParameterInURLPathValidator(TestInterface::class.java).validate()

        assertTrue(result.errors.isEmpty())
    }
}
