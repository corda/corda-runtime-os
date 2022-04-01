package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HttpRpcInterfaceValidatorTest {
    @Test
    fun `validate withMultipleErrors errorListContainsAllMessages`() {
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            @HttpRpcGET
            fun test() {
            }
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validateMultiple withMultipleErrors errorListContainsAllMessages`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            @HttpRpcGET
            fun test() {
            }
        }

        @HttpRpcResource(path = "testinterface")
        class TestInterface2 : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test() {
            }
        }

        val inputList = listOf(TestInterface::class.java, TestInterface2::class.java)

        val result = HttpRpcInterfaceValidator.validate(inputList)

        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validate withNoErrors errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            fun test() {
            }
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertEquals(0, result.errors.size) { "Error: ${result.errors}" }
    }
}