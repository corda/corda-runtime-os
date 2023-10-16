package net.corda.messaging.mediator

import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import net.corda.messaging.api.exception.CordaHTTPClientErrorException
import net.corda.messaging.api.exception.CordaHTTPServerErrorException
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RPCClientTest {

    private lateinit var client: RPCClient
    private val payload = "payload".toByteArray()
    private val message = MediatorMessage(
        payload,
        mutableMapOf(MSG_PROP_ENDPOINT to "http://test-endpoint/api/5.1/test")
    )

    data class Mocks(
        val httpClient: HttpClient,
        val httpResponse: HttpResponse<ByteArray>
    )

    private inner class MockEnvironment(
        val mockHttpClient: HttpClient = mock(),
        val mockHttpResponse: HttpResponse<ByteArray> = mock()
    ) {
        init {
            whenever(mockHttpResponse.statusCode())
                .thenReturn(200)

            whenever(mockHttpResponse.body())
                .thenReturn("responsePayload".toByteArray())

            whenever(mockHttpClient.send(any(), any<HttpResponse.BodyHandler<*>>()))
                .thenReturn(mockHttpResponse)
        }

        fun withHttpStatus(status: Int) = apply {
            whenever(mockHttpResponse.statusCode()).thenReturn(status)
        }

        val mocks: Mocks
            get() = Mocks(mockHttpClient, mockHttpResponse)
    }


    private fun createClient(
        mocks: Mocks,
        httpClientFactory: () -> HttpClient = { mocks.httpClient }
    ): RPCClient {
        return RPCClient(
            "TestRPCClient1",
            httpClientFactory
        )
    }

    @BeforeEach
    fun setup() {
        val environment = MockEnvironment()
        client = createClient(environment.mocks)
    }

    @Test
    fun `send() processes message and returns result`() {
        val result = client.send(message)
        assertNotNull(result?.payload)
        assertTrue("responsePayload"
            .toByteArray()
            .contentEquals(result!!.payload as ByteArray)
        )
    }

    @Test
    fun `send() handles 4XX error`() {
        val environment = MockEnvironment()
            .withHttpStatus(404)

        val client = createClient(environment.mocks)

        assertThrows<CordaHTTPClientErrorException> {
            client.send(message)
        }
    }

    @Test
    fun `send() handles 5XX error`() {
        val environment = MockEnvironment()
            .withHttpStatus(500)

        val client = createClient(environment.mocks)

        assertThrows<CordaHTTPServerErrorException> {
            client.send(message)
        }
    }

    @Test
    fun `send retries on IOException and eventually succeeds`() {
        val environment = MockEnvironment().apply {
            whenever(mockHttpClient.send(any(), any<HttpResponse.BodyHandler<*>>()))
                .thenThrow(IOException("Simulated IO exception"))
                .thenThrow(IOException("Simulated IO exception"))
                .thenReturn(mockHttpResponse)
        }

        val client = createClient(environment.mocks)
        val result = client.send(message)

        assertNotNull(result?.payload)
        assertTrue("responsePayload"
            .toByteArray()
            .contentEquals(result!!.payload as ByteArray)
        )
    }

    @Test
    fun `send fails after exhausting all retries`() {
        val environment = MockEnvironment().apply {
            whenever(mockHttpClient.send(any(), any<HttpResponse.BodyHandler<*>>()))
                .thenThrow(IOException("Simulated IO exception"))
        }

        val client = createClient(environment.mocks)

        assertThrows<IOException> {
            client.send(message)
        }
    }

    @Test
    fun `send retries the correct number of times before failing`() {
        val environment = MockEnvironment().apply {
            whenever(mockHttpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<*>>()))
                .thenThrow(IOException("Simulated IO exception"))
        }

        val client = createClient(environment.mocks)

        assertThrows<IOException> {
            client.send(message)
        }

        verify(environment.mockHttpClient, times(3))
            .send(any<HttpRequest>(), any<HttpResponse.BodyHandler<*>>())
    }
}
