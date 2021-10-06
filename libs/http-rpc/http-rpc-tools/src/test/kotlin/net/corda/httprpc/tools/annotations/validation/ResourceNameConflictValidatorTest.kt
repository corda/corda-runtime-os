package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResourceNameConflictValidatorTest {
    @Test
    fun `validate withResourceDuplicateNames errorListContainsMessage`() {
        @HttpRpcResource(path = "test")
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRpcResource(path = "test")
        class TestInterface2 : RpcOps {
            override val protocolVersion: Int
                get() = 1
        }

        val result = ResourceNameConflictValidator(
            listOf(
                TestInterface::class.java,
                TestInterface2::class.java
            )
        ).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withResourceDuplicateDefaultNames errorListContainsMessage`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRpcResource(path = "TestInterface")
        class TestInterface2 : RpcOps {
            override val protocolVersion: Int
                get() = 1
        }

        val result = ResourceNameConflictValidator(
            listOf(
                TestInterface::class.java,
                TestInterface2::class.java
            )
        ).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withResourceDuplicateCapitalizedNames errorListContainsMessage`() {
        @HttpRpcResource(path = "test")
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRpcResource(path = "Test")
        class TestInterface2 : RpcOps {
            override val protocolVersion: Int
                get() = 1
        }

        val result = ResourceNameConflictValidator(
            listOf(
                TestInterface::class.java,
                TestInterface2::class.java
            )
        ).validate()

        assertEquals(1, result.errors.size)
    }
}