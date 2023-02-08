package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpRestResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RestInterfaceValidatorTest {
    @Test
    fun `validate withMultipleErrors errorListContainsAllMessages`() {
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            @HttpGET
            fun test() {
            }
        }

        val result = RestInterfaceValidator.validate(TestInterface::class.java)

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validateMultiple withMultipleErrors errorListContainsAllMessages`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            @HttpGET
            fun test() {
            }
        }

        @HttpRestResource(path = "testinterface")
        class TestInterface2 : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            fun test() {
            }
        }

        val inputList = listOf(TestInterface::class.java, TestInterface2::class.java)

        val result = RestInterfaceValidator.validate(inputList)

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validate withNoErrors errorListIsEmpty`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET
            fun test() {
            }
        }

        val result = RestInterfaceValidator.validate(TestInterface::class.java)

        assertEquals(0, result.errors.size) { "Error: ${result.errors}" }
    }
}