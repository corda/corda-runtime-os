package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResourceMinMaxVersionValidatorTest {

    @Test
    fun `check valid versions`() {
        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_2)
        abstract class TestInterface : RestResource

        val result = ResourceMinMaxVersionValidator(
            listOf(
                TestInterface::class.java,
            )
        ).validate()

        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `check invalid versions`() {
        @HttpRestResource(path = "test", minVersion = RestApiVersion.C5_2, maxVersion = RestApiVersion.C5_1)
        abstract class TestInterface : RestResource

        val result = ResourceMinMaxVersionValidator(
            listOf(
                TestInterface::class.java,
            )
        ).validate()

        assertThat(result.errors).hasSize(1).contains(ResourceMinMaxVersionValidator.error(TestInterface::class.java))
    }
}
