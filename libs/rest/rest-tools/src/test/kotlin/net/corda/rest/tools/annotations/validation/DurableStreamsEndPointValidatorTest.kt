package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.durablestream.api.DurableCursorBuilder
import net.corda.rest.durablestream.api.FiniteDurableCursorBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DurableStreamsEndPointValidatorTest {

    @Test
    fun `validate with GET Endpoint DurableStreamsReturnType errorListContainsMessage`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET
            abstract fun test(): DurableCursorBuilder<String>
        }

        val result = DurableStreamsEndPointValidator(TestInterface::class.java).validate()

        Assertions.assertEquals(1, result.errors.size)
        Assertions.assertEquals(DurableStreamsEndPointValidator.error, result.errors.single())
    }

    @Test
    fun `validate with GET Endpoint FiniteDurableStreamsReturnType errorListContainsMessage`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET
            abstract fun test(): FiniteDurableCursorBuilder<String>
        }

        val result = DurableStreamsEndPointValidator(TestInterface::class.java).validate()

        Assertions.assertEquals(1, result.errors.size)
        Assertions.assertEquals(DurableStreamsEndPointValidator.error, result.errors.single())
    }

    @Test
    fun `validate with POST Endpoint DurableStreamsReturnType errorList Is Empty`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            abstract fun test(): DurableCursorBuilder<String>
        }

        val result = DurableStreamsEndPointValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(0, result.errors.size)
    }
}