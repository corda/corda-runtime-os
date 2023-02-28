package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.HttpRestResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class URLPathParameterNotDeclaredValidatorTest {
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

        val result = URLPathParameterNotDeclaredValidator(TestInterface::class.java).validate()

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

        val result = URLPathParameterNotDeclaredValidator(TestInterface::class.java).validate()

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

        val result = URLPathParameterNotDeclaredValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withMultiplePathParamsNotExisting errorListContainsAllErrors`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET(path = "abc/{param}/{param2}/def")
            fun test() {
            }
        }

        val result = URLPathParameterNotDeclaredValidator(TestInterface::class.java).validate()

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validate withPathParamsWithDifferentCase errorListIsEmpty`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET(path = "abc/{FOO2}/def")
            fun test(@RestPathParameter foo2: String) {
                foo2.lowercase()
            }
        }

        val result = URLPathParameterNotDeclaredValidator(TestInterface::class.java).validate()

        assertTrue(result.errors.isEmpty())
    }
}
