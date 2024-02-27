package net.corda.messaging.utils

import net.corda.messaging.api.exception.CordaHTTPClientErrorException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.net.http.HttpResponse

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
        val mockResponse: HttpResponse<ByteArray> = mock()
        whenever(mockResponse.body()).thenReturn("Success".toByteArray(Charsets.UTF_8))

        val result: HttpResponse<ByteArray> = HTTPRetryExecutor.withConfig(retryConfig) {
            mockResponse
        }

        assertEquals("Success", result.body().toString(Charsets.UTF_8))
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `should retry until successful`() {
        val mockResponse: HttpResponse<ByteArray> = mock()
        whenever(mockResponse.body()).thenReturn("Success on attempt 3".toByteArray(Charsets.UTF_8))

        var attempt = 0

        val result: HttpResponse<ByteArray> = HTTPRetryExecutor.withConfig(retryConfig) {
            ++attempt
            if (attempt < 3) {
                throw RuntimeException("Failed on attempt $attempt")
            }
            mockResponse
        }

        assertEquals("Success on attempt 3", result.body().toString(Charsets.UTF_8))
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `should throw exception after max attempts`() {
        var attempt = 0

        assertThrows<RuntimeException> {
            HTTPRetryExecutor.withConfig(retryConfig) {
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
            HTTPRetryExecutor.withConfig(config) {
                throw RuntimeException("I'm not retryable!")
            }
        }
    }

    @Test
    fun `should retry on client error status code`() {
        val mockResponse: HttpResponse<ByteArray> = mock()
        whenever(mockResponse.body()).thenReturn("Success on attempt 3".toByteArray(Charsets.UTF_8))
        val config = HTTPRetryConfig.Builder()
            .times(3)
            .initialDelay(100)
            .factor(2.0)
            .retryOn(SpecificException::class.java)
            .retryOn(CordaHTTPClientErrorException::class.java)
            .build()

        var attempt = 0

        val result: HttpResponse<ByteArray> = HTTPRetryExecutor.withConfig(config) {
            ++attempt
            if (attempt < 3) {
                throw CordaHTTPClientErrorException(404, "Not Found on attempt $attempt")
            }
            mockResponse
        }

        assertEquals("Success on attempt 3", result.body().toString(Charsets.UTF_8))
    }

    @Test
    fun `retryOn inherited exception`() {
        val mockResponse: HttpResponse<ByteArray> = mock()
        whenever(mockResponse.body()).thenReturn("Success".toByteArray(Charsets.UTF_8))
        val config = HTTPRetryConfig.Builder()
            .times(3)
            .initialDelay(100)
            .factor(2.0)
            .retryOn(BaseException::class.java)
            .build()

        var attempt = 0

        val result: HttpResponse<ByteArray> = HTTPRetryExecutor.withConfig(config) {
            ++attempt
            if (attempt < 3) {
                throw InheritedException("Inherited")
            }
            mockResponse
        }

        assertEquals("Success", result.body().toString(Charsets.UTF_8))
    }

    internal class SpecificException(message: String) : Exception(message)
    internal open class BaseException(message: String) : Exception(message)
    internal class InheritedException(message: String) : BaseException(message)
}
