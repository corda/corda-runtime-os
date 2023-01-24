package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.annotations.HttpRpcWS
import net.corda.httprpc.ws.DuplexChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParameterBodyAnnotationValidatorTest {
    @Test
    fun `validate withInvalidBodyAnnotation errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            fun test(@HttpRpcRequestBodyParameter foo: String) {
                foo.lowercase()
            }

            @HttpRpcGET
            fun testWithImplicitBodyParam(foo: String) {
                foo.lowercase()
            }

            @HttpRpcWS
            fun testWithImplicitBodyParamWS(channel: DuplexChannel, foo: String) {
                foo.lowercase()
                channel.close()
            }
        }

        val result = ParameterBodyAnnotationValidator(TestInterface::class.java)
            .validate()

        assertThat(result.errors).hasSize(3).allMatch { it == "GET/DELETE/WS requests are not allowed to have a body" }
    }

    @Test
    fun `validate withManyBodyParametersAnnotations errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcPOST
            fun test(@HttpRpcRequestBodyParameter foo: String, @HttpRpcRequestBodyParameter bar: String) {
                foo.lowercase()
                bar.lowercase()
            }

            @HttpRpcPOST
            fun testWithImplicitBodyParam(foo: String, bar: String) {
                foo.lowercase()
                bar.lowercase()
            }

            @HttpRpcWS
            fun testWithImplicitQueryParamWS(channel: DuplexChannel, @HttpRpcQueryParameter foo: String) {
                foo.lowercase()
                channel.close()
            }
        }

        val result = ParameterBodyAnnotationValidator(TestInterface::class.java)
            .validate()

        assert(result.errors.isEmpty())
    }
}
