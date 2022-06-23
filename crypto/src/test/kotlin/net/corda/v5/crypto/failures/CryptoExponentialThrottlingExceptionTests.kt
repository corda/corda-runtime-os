package net.corda.v5.crypto.failures

import net.corda.v5.crypto.failures.CryptoExponentialRetryStrategy.Companion.DEFAULT_THROTTLE_INITIAL_BACKOFF
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CryptoExponentialThrottlingExceptionTests {
    @Test
    fun `Should return default increasing backoff`() {
        val e =  CryptoExponentialThrottlingException("Something wrong.", RuntimeException())
        var backOff = 0L
        backOff = e.getBackoff(1, backOff)
        assertEquals(DEFAULT_THROTTLE_INITIAL_BACKOFF, backOff)
        backOff = e.getBackoff(2, backOff)
        assertEquals(DEFAULT_THROTTLE_INITIAL_BACKOFF * 2, backOff)
        backOff = e.getBackoff(3, backOff)
        assertEquals(DEFAULT_THROTTLE_INITIAL_BACKOFF * 4, backOff)
        backOff = e.getBackoff(4, backOff)
        assertEquals(DEFAULT_THROTTLE_INITIAL_BACKOFF * 8, backOff)
        backOff = e.getBackoff(5, backOff)
        assertEquals(DEFAULT_THROTTLE_INITIAL_BACKOFF * 16, backOff)
        backOff = e.getBackoff(6, backOff)
        assertEquals(-1, backOff)
    }

    @Test
    fun `Should return custom increasing backoff`() {
        val e =  CryptoExponentialThrottlingException(
            "Something wrong.",
            3_000L,
            3L,
            5,
            RuntimeException()
        )
        var backOff = 0L
        backOff = e.getBackoff(1, backOff)
        assertEquals(3_000L, backOff)
        backOff = e.getBackoff(2, backOff)
        assertEquals(3_000L * 3, backOff)
        backOff = e.getBackoff(3, backOff)
        assertEquals(3_000L * 9, backOff)
        backOff = e.getBackoff(4, backOff)
        assertEquals(3_000L * 27, backOff)
        backOff = e.getBackoff(5, backOff)
        assertEquals(-1, backOff)
    }
}