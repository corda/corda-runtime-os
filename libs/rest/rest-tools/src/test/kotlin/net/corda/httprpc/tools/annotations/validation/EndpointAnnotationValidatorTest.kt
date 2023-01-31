package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpRestResource
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