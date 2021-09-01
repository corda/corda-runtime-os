package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.tools.annotations.validation.ResourceNameConflictValidator
import net.corda.v5.application.messaging.RPCOps
import net.corda.v5.httprpc.api.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResourceNameConflictValidatorTest {
    @Test
    fun `validate withResourceDuplicateNames errorListContainsMessage`() {
        @HttpRpcResource(path = "test")
        class TestInterface : RPCOps {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRpcResource(path = "test")
        class TestInterface2 : RPCOps {
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
        class TestInterface : RPCOps {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRpcResource(path = "TestInterface")
        class TestInterface2 : RPCOps {
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
        class TestInterface : RPCOps {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRpcResource(path = "Test")
        class TestInterface2 : RPCOps {
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