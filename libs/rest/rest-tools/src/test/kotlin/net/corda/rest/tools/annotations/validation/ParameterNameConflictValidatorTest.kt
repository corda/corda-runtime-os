package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpRestResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParameterNameConflictValidatorTest {
    @Test
    fun `validate withSameParamNames errorListContainsError`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET
            fun test(@RestQueryParameter(name = "foo") foo1: String, @RestQueryParameter(name = "foo") foo2: String) {
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
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET
            fun test(@RestQueryParameter(name = "foo") foo1: String, @RestQueryParameter foo: String) {
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
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET
            fun test(
                @RestQueryParameter(name = "foo") foo1: String,
                @RestQueryParameter Foo: String,
                @RestQueryParameter(name = "FOO") foo2: String
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
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET
            fun test(@RestQueryParameter(name = "foo") foo1: String, @RestPathParameter(name = "foo") foo2: String) {
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
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET
            fun test(foO: String, @ClientRequestBodyParameter(name = "Foo") foo2: String) {
                foO.lowercase()
                foo2.lowercase()
            }
        }

        val result = ParameterNameConflictValidator(TestInterface::class.java)
            .validate()

        assertEquals(1, result.errors.size)
    }
}
