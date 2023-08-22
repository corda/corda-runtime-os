package net.corda.web.client

import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever


class CordaHttpClientImplTest {

    private val mockHttpClient: HttpClient = mock()
    private val mockHttpResponse: HttpResponse<ByteArray> = mock()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `post sends request and returns response`() = runTest(UnconfinedTestDispatcher()) {
        val client = CordaHttpClientImpl().apply {
            this.client = mockHttpClient
        }

        val testUrl = URL("https://example.com")
        val testPayload = "testPayload".toByteArray()
        val expectedResponse = "testResponse".toByteArray()

        whenever(mockHttpClient.sendAsync(any<HttpRequest>(), any<HttpResponse.BodyHandler<ByteArray>>()))
            .thenReturn(completableFutureOf(mockHttpResponse))

        whenever(mockHttpResponse.body()).thenReturn(expectedResponse)

        val response = client.post(testUrl, testPayload)

        assertEquals(expectedResponse, response)
    }

    private fun <T> completableFutureOf(value: T) = CompletableFuture.completedFuture(value)
}


