package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.durablestream.api.DurableCursorBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DurableStreamsContextParameterValidatorTest {

    @Test
    fun `validate with POST Endpoint DurableStreamsReturnType Context BodyParameter errorListContainsMessage`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            abstract fun test(@ClientRequestBodyParameter context: String): DurableCursorBuilder<String>

            @HttpPOST
            abstract fun test2(@ClientRequestBodyParameter(name = "context") notContext: String): DurableCursorBuilder<String>

            @HttpPOST
            abstract fun testImplicitBodyParam(context: String): DurableCursorBuilder<String>
        }

        val result = DurableStreamsContextParameterValidator(TestInterface::class.java).validate()

        Assertions.assertEquals(3, result.errors.size)
        Assertions.assertEquals(DurableStreamsContextParameterValidator.error, result.errors.first())
    }

    @Test
    fun `validate with POST Endpoint DurableStreamsReturnType Context Query OR Path Parameter errorListEmpty`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            abstract fun test(@RestQueryParameter context: String): DurableCursorBuilder<String>

            @HttpPOST
            abstract fun test2(@RestQueryParameter(name = "context") notContext: String): DurableCursorBuilder<String>

            @HttpPOST
            abstract fun test3(@RestPathParameter context: String): DurableCursorBuilder<String>

            @HttpPOST
            abstract fun test4(@RestPathParameter(name = "context") notContext: String): DurableCursorBuilder<String>
        }

        val result = DurableStreamsContextParameterValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(0, result.errors.size)
    }

    @Test
    fun `validate with GET Endpoint DurableStreamsReturnType Context BodyParameter errorListEmpty`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET
            abstract fun test(@ClientRequestBodyParameter context: String): DurableCursorBuilder<String>

            @HttpGET
            abstract fun test2(@ClientRequestBodyParameter(name = "context") notContext: String): DurableCursorBuilder<String>

            @HttpGET
            abstract fun testImplicitBodyParam(context: String): DurableCursorBuilder<String>
        }

        val result = DurableStreamsContextParameterValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(0, result.errors.size)
    }

    @Test
    fun `validate with POST Endpoint DurableStreamsReturnType BodyParameter isNotCalledContext errorListEmpty`() {
        @Suppress("unused")
        @HttpRestResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            abstract fun test(@ClientRequestBodyParameter notContext: String): DurableCursorBuilder<String>

            @HttpPOST
            abstract fun test2(@ClientRequestBodyParameter(name = "contextOverriden") context: String): DurableCursorBuilder<String>

            @HttpPOST
            abstract fun testImplicitBodyParam(notContext: String): DurableCursorBuilder<String>
        }

        val result = DurableStreamsContextParameterValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(0, result.errors.size)
    }
}