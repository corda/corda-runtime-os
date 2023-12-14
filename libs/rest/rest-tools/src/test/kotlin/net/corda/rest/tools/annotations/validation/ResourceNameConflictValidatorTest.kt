package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.tools.annotations.validation.ResourceNameConflictValidator.Companion.error
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResourceNameConflictValidatorTest {
    @Test
    fun `validate withResourceDuplicateNames errorListContainsMessage`() {
        @HttpRestResource(path = "test")
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRestResource(path = "test")
        class TestInterface2 : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        val result = ResourceNameConflictValidator(
            listOf(
                TestInterface::class.java,
                TestInterface2::class.java
            )
        ).validate()

        val expectedErrors = listOf(error("test", TestInterface2::class.java, TestInterface::class.java))
        assertEquals(expectedErrors, result.errors)
    }

    @Test
    fun `validate withResourceDuplicateNamesDifferentVersions errorListIsEmpty`() {
        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_1)
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_2, maxVersion = RestApiVersion.C5_2)
        class TestInterface2 : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        val result = ResourceNameConflictValidator(
            listOf(
                TestInterface::class.java,
                TestInterface2::class.java
            )
        ).validate()

        assertEquals(emptyList<String>(), result.errors)
    }

    @Test
    fun `validate withResourceDuplicateNamesOverlappingVersions errorListContainsMessage`() {
        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_1)
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_1, maxVersion = RestApiVersion.C5_2)
        class TestInterface2 : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        val result = ResourceNameConflictValidator(
            listOf(
                TestInterface::class.java,
                TestInterface2::class.java
            )
        ).validate()

        val expectedErrors = listOf(error("test", TestInterface2::class.java, TestInterface::class.java))
        assertEquals(expectedErrors, result.errors)
    }

    @Test
    fun `validate withResourceDuplicateNamesContainedVersions errorListContainsMessage`() {
        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_2)
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_1, maxVersion = RestApiVersion.C5_1)
        class TestInterface2 : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        val result = ResourceNameConflictValidator(
            listOf(
                TestInterface::class.java,
                TestInterface2::class.java
            )
        ).validate()

        val expectedErrors = listOf(error("test", TestInterface2::class.java, TestInterface::class.java))
        assertEquals(expectedErrors, result.errors)
    }

    @Test
    fun `validate withResourceDuplicateNamesMultipleConflicts errorListContainsMessages`() {
        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_0)
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_2, maxVersion = RestApiVersion.C5_2)
        class TestInterface2 : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_2)
        class TestInterface3 : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        val result = ResourceNameConflictValidator(
            listOf(
                TestInterface::class.java,
                TestInterface2::class.java,
                TestInterface3::class.java
            )
        ).validate()

        val expectedErrors = listOf(
            error("test", TestInterface3::class.java, TestInterface::class.java),
            error("test", TestInterface3::class.java, TestInterface2::class.java),
        )
        assertEquals(expectedErrors, result.errors)
    }

    @Test
    fun `validate withResourceDuplicateDefaultNames errorListContainsMessage`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRestResource(path = "TestInterface")
        class TestInterface2 : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        val result = ResourceNameConflictValidator(
            listOf(
                TestInterface::class.java,
                TestInterface2::class.java
            )
        ).validate()

        val expectedErrors = listOf(error("testinterface", TestInterface2::class.java, TestInterface::class.java))
        assertEquals(expectedErrors, result.errors)
    }

    @Test
    fun `validate withResourceDuplicateCapitalizedNames errorListContainsMessage`() {
        @HttpRestResource(path = "test")
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1
        }

        @HttpRestResource(path = "Test")
        class TestInterface2 : RestResource {
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
