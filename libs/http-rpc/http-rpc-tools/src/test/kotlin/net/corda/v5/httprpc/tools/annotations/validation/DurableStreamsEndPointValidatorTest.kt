package net.corda.v5.httprpc.tools.annotations.validation

import net.corda.v5.httprpc.api.RpcOps
import net.corda.v5.base.stream.DurableCursorBuilder
import net.corda.v5.base.stream.FiniteDurableCursorBuilder
import net.corda.v5.httprpc.api.annotations.HttpRpcGET
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.api.annotations.HttpRpcResource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DurableStreamsEndPointValidatorTest {

    @Test
    fun `validate with GET Endpoint DurableStreamsReturnType errorListContainsMessage`() {
        @Suppress("unused")
        @HttpRpcResource
        abstract class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            abstract fun test(): DurableCursorBuilder<String>
        }

        val result = DurableStreamsEndPointValidator(TestInterface::class.java).validate()

        Assertions.assertEquals(1, result.errors.size)
        Assertions.assertEquals(DurableStreamsEndPointValidator.error, result.errors.single())
    }

    @Test
    fun `validate with GET Endpoint FiniteDurableStreamsReturnType errorListContainsMessage`() {
        @Suppress("unused")
        @HttpRpcResource
        abstract class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            abstract fun test(): FiniteDurableCursorBuilder<String>
        }

        val result = DurableStreamsEndPointValidator(TestInterface::class.java).validate()

        Assertions.assertEquals(1, result.errors.size)
        Assertions.assertEquals(DurableStreamsEndPointValidator.error, result.errors.single())
    }

    @Test
    fun `validate with POST Endpoint DurableStreamsReturnType errorList Is Empty`() {
        @Suppress("unused")
        @HttpRpcResource
        abstract class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            abstract fun test(): DurableCursorBuilder<String>
        }

        val result = DurableStreamsEndPointValidator(TestInterface::class.java).validate()
        Assertions.assertEquals(0, result.errors.size)
    }
}