package net.corda.httprpc.tools.annotations.validation

import net.corda.v5.application.messaging.RPCOps
import net.corda.v5.httprpc.api.annotations.HttpRpcGET
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.api.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

class EndpointAnnotationValidatorTest {
    @Test
    fun `validate withMultipleEndpointVerbsInSameFunction errorListContainsMessage`() {
        @Suppress("unused")
        @HttpRpcResource
        class TestInterface : RPCOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            @HttpRpcGET
            fun test() {
            }
        }

        val result = EndpointAnnotationValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
        assertEquals(EndpointAnnotationValidator.error(TestInterface::test.javaMethod!!), result.errors.single())
    }
}