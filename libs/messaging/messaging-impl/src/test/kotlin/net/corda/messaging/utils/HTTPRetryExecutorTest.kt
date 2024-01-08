package net.corda.messaging.utils

import java.net.http.HttpResponse
import net.corda.messaging.api.exception.CordaHTTPClientErrorException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class HTTPRetryExecutorTest {
    private lateinit var retryConfig: HTTPRetryConfig

    @BeforeEach
    fun setUp() {
        retryConfig = HTTPRetryConfig.Builder()
            .times(3)
            .initialDelay(100)
            .factor(2.0)
            .retryOn(RuntimeException::class.java)
            .build()
    }

    @Test
    fun `successfully returns after first attempt`() {
        val mockResponse: HttpResponse<String> = mock()
        whenever(mockResponse.body()).thenReturn("Success")

        val result: HttpResponse<String> = HTTPRetryExecutor.withConfig(retryConfig) {
            mockResponse
        }

        assertEquals("Success", result.body())
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `should retry until successful`() {
        val mockResponse: HttpResponse<String> = mock()
        whenever(mockResponse.body()).thenReturn("Success on attempt 3")

        var attempt = 0

        val result: HttpResponse<String> = HTTPRetryExecutor.withConfig(retryConfig) {
            ++attempt
            if (attempt < 3) {
                throw RuntimeException("Failed on attempt $attempt")
            }
            mockResponse
        }

        assertEquals("Success on attempt 3", result.body())
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `should throw exception after max attempts`() {
        var attempt = 0

        assertThrows<RuntimeException> {
            HTTPRetryExecutor.withConfig<String>(retryConfig) {
                ++attempt
                throw RuntimeException("Failed on attempt $attempt")
            }
        }
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `should not retry on non-retryable exception`() {
        val config = HTTPRetryConfig.Builder()
            .times(3)
            .initialDelay(100)
            .factor(2.0)
            .retryOn(SpecificException::class.java)
            .build()

        assertThrows<RuntimeException> {
            HTTPRetryExecutor.withConfig<String>(config) {
                throw RuntimeException("I'm not retryable!")
            }
        }
    }

    @Test
    fun `should retry on client error status code`() {
        val mockResponse: HttpResponse<String> = mock()
        whenever(mockResponse.body()).thenReturn("Success on attempt 3")
        val config = HTTPRetryConfig.Builder()
            .times(3)
            .initialDelay(100)
            .factor(2.0)
            .retryOn(SpecificException::class.java)
            .retryOn(CordaHTTPClientErrorException::class.java)
            .build()

        var attempt = 0

        val result: HttpResponse<String> = HTTPRetryExecutor.withConfig(config) {
            ++attempt
            if (attempt < 3) {
                throw CordaHTTPClientErrorException(404, "Not Found on attempt $attempt")
            }
            mockResponse
        }

        assertEquals("Success on attempt 3", result.body())
    }

    internal class SpecificException(message: String) : Exception(message)
}
