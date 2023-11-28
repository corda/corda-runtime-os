package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.HttpWS
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.ws.DuplexChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParameterBodyAnnotationValidatorTest {
    @Test
    fun `validate withInvalidBodyAnnotation errorListContainsError`() {
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpGET
            fun test(@ClientRequestBodyParameter foo: String) {
                foo.lowercase()
            }

            @HttpGET
            fun testWithImplicitBodyParam(foo: String) {
                foo.lowercase()
            }

            @HttpWS
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
        @HttpRestResource
        class TestInterface : RestResource {
            override val protocolVersion: Int
                get() = 1

            @HttpPOST
            fun test(@ClientRequestBodyParameter foo: String, @ClientRequestBodyParameter bar: String) {
                foo.lowercase()
                bar.lowercase()
            }

            @HttpPOST
            fun testWithImplicitBodyParam(foo: String, bar: String) {
                foo.lowercase()
                bar.lowercase()
            }

            @HttpWS
            fun testWithImplicitQueryParamWS(channel: DuplexChannel, @RestQueryParameter foo: String) {
                foo.lowercase()
                channel.close()
            }
        }

        val result = ParameterBodyAnnotationValidator(TestInterface::class.java)
            .validate()

        assert(result.errors.isEmpty())
    }
}
