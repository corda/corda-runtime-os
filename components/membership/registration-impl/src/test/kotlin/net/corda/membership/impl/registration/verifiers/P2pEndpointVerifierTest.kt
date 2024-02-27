package net.corda.membership.impl.registration.verifiers

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class P2pEndpointVerifierTest {
    private val orderVerifier = mock<OrderVerifier> {
        on { isOrdered(any(), any()) } doReturn true
    }
    private val p2pEndpointVerifier = P2pEndpointVerifier(orderVerifier)

    @Test
    fun `valid context will be verified`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://www.r3.com:8080",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888/",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        p2pEndpointVerifier.verifyContext(context)
    }

    @Test
    fun `context with no URL will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThatThrownBy {
            p2pEndpointVerifier.verifyContext(context)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("No endpoint URL was provided.")
    }

    @Test
    fun `context with no protocol version will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://www.r3.com:8080",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
        )
        assertThatThrownBy {
            p2pEndpointVerifier.verifyContext(context)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("No endpoint protocol was provided.")
    }

    @Test
    fun `context with out of order URLs will throw an exception`() {
        whenever(
            orderVerifier.isOrdered(
                listOf(
                    "corda.endpoints.0.connectionURL",
                    "corda.endpoints.1.connectionURL",
                ),
                2
            )
        ).thenReturn(false)
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://www.r3.com:8080",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThatThrownBy {
            p2pEndpointVerifier.verifyContext(context)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Provided endpoint URLs are incorrectly numbered.")
    }

    @Test
    fun `context with out of order protocol version will throw an exception`() {
        whenever(
            orderVerifier.isOrdered(
                listOf(
                    "corda.endpoints.0.protocolVersion",
                    "corda.endpoints.1.protocolVersion",
                ),
                2
            )
        ).thenReturn(false)
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://www.r3.com:8080",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThatThrownBy {
            p2pEndpointVerifier.verifyContext(context)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Provided endpoint protocols are incorrectly numbered.")
    }

    @Test
    fun `context with invalid URL will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "hi there",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThatThrownBy {
            p2pEndpointVerifier.verifyContext(context)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Endpoint URL ('hi there') is not a valid URL.")
    }

    @Test
    fun `context with URL without a port will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://r3.com/",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThatThrownBy {
            p2pEndpointVerifier.verifyContext(context)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("The port of the endpoint URL ('https://r3.com/') was not specified or had an invalid value.")
    }

    @Test
    fun `context with URL without a host will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://:4995/",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThatThrownBy {
            p2pEndpointVerifier.verifyContext(context)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("The host of the endpoint URL ('https://:4995/') was not specified or had an invalid value.")
    }

    @Test
    fun `context with URL without https will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "http://www.corda.net:8888",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThatThrownBy {
            p2pEndpointVerifier.verifyContext(context)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("The scheme of the endpoint URL ('http://www.corda.net:8888') was not https.")
    }

    @Test
    fun `context with auth info will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://username:password@www.corda.net:8888",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThatThrownBy {
            p2pEndpointVerifier.verifyContext(context)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining(
                "Endpoint URL ('https://username:password@www.corda.net:8888') " +
                    "had user info specified, which must not be specified."
            )
    }

    @Test
    fun `duplicate URLs will fail validation`() {
        val url1 = "https://www.r3.com:8080"
        val url2 = "https://www.corda.net:8888"

        val context = mapOf(
            "corda.endpoints.0.connectionURL" to url1,
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to url1,
            "corda.endpoints.1.protocolVersion" to "1",
            "corda.endpoints.2.connectionURL" to url2,
            "corda.endpoints.2.protocolVersion" to "1",
            "corda.endpoints.3.connectionURL" to url2,
            "corda.endpoints.3.protocolVersion" to "1",
        )

        assertThatThrownBy {
            p2pEndpointVerifier.verifyContext(context)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Duplicate connection URLs found")
            .hasMessageContaining(url1)
            .hasMessageContaining(url2)
    }
}
