package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.durablestream.api.DurableCursorBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DurableStreamsContextParameterValidatorTest {

    @Test
    fun `validate with POST Endpoint DurableStreamsReturnType Context BodyParameter errorListContainsMessage`() {
        @Suppress("unused")
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            abstract fun test(@HttpRpcRequestBodyParameter context: String): DurableCursorBuilder<String>

            @HttpRpcPOST
            abstract fun test2(@HttpRpcRequestBodyParameter(name = "context") notContext: String): DurableCursorBuilder<String>

            @HttpRpcPOST
            abstract fun testImplicitBodyParam(context: String): DurableCursorBuilder<String>
        }

        val result = DurableStreamsContextParameterValidator(TestInterface::class.java).validate()

        Assertions.assertEquals(3, result.errors.size)
        Assertions.assertEquals(DurableStreamsContextParameterValidator.error, result.errors.first())
    }

    @Test
    fun `validate with POST Endpoint DurableStreamsReturnType Context Query OR Path Parameter errorListEmpty`() {
        @Suppress("unused")
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            abstract fun test(@HttpRpcQueryParameter context: String): DurableCursorBuilder<String>

            @HttpRpcPOST
            abstract fun test2(@HttpRpcQueryParameter(name = "context") notContext: String): DurableCursorBuilder<String>

            @HttpRpcPOST
            abstract fun test3(@HttpRpcPathParameter context: String): DurableCursorBuilder<String>

            @HttpRpcPOST
            abstract fun test4(@HttpRpcPathParameter(name = "context") notContext: String): DurableCursorBuilder<String>
        }

        val result = DurableStreamsContextParameterValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(0, result.errors.size)
    }

    @Test
    fun `validate with GET Endpoint DurableStreamsReturnType Context BodyParameter errorListEmpty`() {
        @Suppress("unused")
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            abstract fun test(@HttpRpcRequestBodyParameter context: String): DurableCursorBuilder<String>

            @HttpRpcGET
            abstract fun test2(@HttpRpcRequestBodyParameter(name = "context") notContext: String): DurableCursorBuilder<String>

            @HttpRpcGET
            abstract fun testImplicitBodyParam(context: String): DurableCursorBuilder<String>
        }

        val result = DurableStreamsContextParameterValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(0, result.errors.size)
    }

    @Test
    fun `validate with POST Endpoint DurableStreamsReturnType BodyParameter isNotCalledContext errorListEmpty`() {
        @Suppress("unused")
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            abstract fun test(@HttpRpcRequestBodyParameter notContext: String): DurableCursorBuilder<String>

            @HttpRpcPOST
            abstract fun test2(@HttpRpcRequestBodyParameter(name = "contextOverriden") context: String): DurableCursorBuilder<String>

            @HttpRpcPOST
            abstract fun testImplicitBodyParam(notContext: String): DurableCursorBuilder<String>
        }

        val result = DurableStreamsContextParameterValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(0, result.errors.size)
    }
}