package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

class EndpointMinMaxVersionValidatorTest {

    @Test
    fun `validate valid versions relationship`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {

            @HttpGET(minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_2)
            abstract fun test()
        }

        val result = EndpointMinMaxVersionValidator(TestInterface::class.java).validate()

        Assertions.assertEquals(0, result.errors.size)
    }

    @Test
    fun `validate invalid versions relationship`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {

            @HttpGET(minVersion = RestApiVersion.C5_2, maxVersion = RestApiVersion.C5_1)
            abstract fun test()
        }

        val result = EndpointMinMaxVersionValidator(TestInterface::class.java).validate()

        Assertions.assertEquals(1, result.errors.size)
        Assertions.assertEquals(
            EndpointMinMaxVersionValidator.error(TestInterface::test.javaMethod!!),
            result.errors.single()
        )
    }
}
