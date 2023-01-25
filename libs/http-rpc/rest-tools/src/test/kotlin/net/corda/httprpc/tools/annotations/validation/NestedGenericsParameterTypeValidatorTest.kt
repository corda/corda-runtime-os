package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpRestResource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

class NestedGenericsParameterTypeValidatorTest {

    @Test
    fun `method returns nested generic types errorListContainsMessage`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            abstract fun test(param: List<List<String>>)
        }

        val result = NestedGenericsParameterTypeValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(1, result.errors.size)
        Assertions.assertEquals(NestedGenericsParameterTypeValidator.error(TestInterface::test.javaMethod!!), result.errors.single())
    }

    @Test
    fun `method returns non nested generic types errorList is Empty`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            abstract fun test(param: List<String>)
        }

        val result = NestedGenericsParameterTypeValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(0, result.errors.size)
    }

    @Test
    fun `method does not return generic types errorList is Empty`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            abstract fun test(param: String)
        }

        val result = NestedGenericsParameterTypeValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(0, result.errors.size)
    }
}