package net.corda.messaging.utils

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HTTPRetryExecutorTest {
    private lateinit var retryConfig: HTTPRetryConfig

    @BeforeEach
    fun setUp() {
        retryConfig = HTTPRetryConfig.Builder()
            .times(3)
            .initialDelay(100)
            .factor(2.0)
            .retryOn(RuntimeException::class)
            .build()
    }

    @Test
    fun `successfully returns after first attempt`() = runBlocking {
        val result = HTTPRetryExecutor.withConfig(retryConfig) {
            "Success"
        }

        assertEquals("Success", result)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `should retry until successful`() = runBlocking {
        var attempt = 0

        val result = HTTPRetryExecutor.withConfig(retryConfig) {
            ++attempt
            if (attempt < 3) {
                throw RuntimeException("Failed on attempt $attempt")
            }
            "Success on attempt $attempt"
        }

        assertEquals("Success on attempt 3", result)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `should throw exception after max attempts`() = runBlocking {
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
    fun `should not retry on non-retryable exception`() = runBlocking  {
        val config = HTTPRetryConfig.Builder()
            .times(3)
            .initialDelay(100)
            .factor(2.0)
            .retryOn(SpecificException::class)
            .build()

        assertThrows<RuntimeException> {
            HTTPRetryExecutor.withConfig(config) {
                throw RuntimeException("I'm not retryable!")
            }
        }
    }

    internal class SpecificException(message: String) : Exception(message)
}