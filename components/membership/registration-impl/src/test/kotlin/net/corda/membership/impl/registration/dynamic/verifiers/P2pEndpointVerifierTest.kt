package net.corda.membership.impl.registration.dynamic.verifiers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

        assertThrows<IllegalArgumentException> {
            p2pEndpointVerifier.verifyContext(context)
        }
    }

    @Test
    fun `context with no protocol version will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://www.r3.com:8080",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
        )

        assertThrows<IllegalArgumentException> {
            p2pEndpointVerifier.verifyContext(context)
        }
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

        assertThrows<IllegalArgumentException> {
            p2pEndpointVerifier.verifyContext(context)
        }
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

        assertThrows<IllegalArgumentException> {
            p2pEndpointVerifier.verifyContext(context)
        }
    }

    @Test
    fun `context with invalid URL will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "hi there",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThrows<IllegalArgumentException> {
            p2pEndpointVerifier.verifyContext(context)
        }
    }

    @Test
    fun `context with URL without a port will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://r3.com/",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThrows<IllegalArgumentException> {
            p2pEndpointVerifier.verifyContext(context)
        }
    }

    @Test
    fun `context with URL without a host will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://:4995/",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThrows<IllegalArgumentException> {
            p2pEndpointVerifier.verifyContext(context)
        }
    }

    @Test
    fun `context with URL without https will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "http://www.corda.net:8888",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThrows<IllegalArgumentException> {
            p2pEndpointVerifier.verifyContext(context)
        }
    }

    @Test
    fun `context with auth info will throw an exception`() {
        val context = mapOf(
            "corda.endpoints.0.connectionURL" to "https://username:password@www.corda.net:8888",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.connectionURL" to "https://www.corda.net:8888",
            "corda.endpoints.1.protocolVersion" to "1",
        )

        assertThrows<IllegalArgumentException> {
            p2pEndpointVerifier.verifyContext(context)
        }
    }
}
