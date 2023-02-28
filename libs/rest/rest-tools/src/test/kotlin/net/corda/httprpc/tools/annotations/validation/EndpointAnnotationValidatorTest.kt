package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

class EndpointAnnotationValidatorTest {
    @Test
    fun `validate withMultipleEndpointVerbsInSameFunction errorListContainsMessage`() {
        @Suppress("unused")
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            @HttpGET
            fun test() {
            }
        }

        val result = EndpointAnnotationValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
        assertEquals(EndpointAnnotationValidator.error(TestInterface::test.javaMethod!!), result.errors.single())
    }
}