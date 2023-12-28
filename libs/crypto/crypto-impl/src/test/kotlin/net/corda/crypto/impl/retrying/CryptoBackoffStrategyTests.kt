package net.corda.crypto.impl.retrying

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CryptoBackoffStrategyTests {
    @Test
    fun `Should return customizes backoff`() {
        val strategy = CryptoBackoffStrategy(3, listOf(100, 200))
        var backoff = strategy.delay(1)
        assertEquals(100L, backoff)
        backoff = strategy.delay(2)
        assertEquals(200L, backoff)
        backoff = strategy.delay(3)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return customizes backoff with repeating value`() {
        val strategy = CryptoBackoffStrategy(3, listOf(300))
        var backoff = strategy.delay(1)
        assertEquals(300L, backoff)
        backoff = strategy.delay(2)
        assertEquals(300L, backoff)
        backoff = strategy.delay(3)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return customizes backoff for attempts 0 and empty list`() {
        val strategy = CryptoBackoffStrategy(0, emptyList())
        val backoff = strategy.delay(1)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return customizes backoff for attempts 1 and empty list`() {
        val strategy = CryptoBackoffStrategy(1, emptyList())
        val backoff = strategy.delay(1)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return customizes backoff for attempts 2 and empty list`() {
        val strategy = CryptoBackoffStrategy(2, emptyList())
        var backoff = strategy.delay(1)
        assertEquals(0L, backoff)
        backoff = strategy.delay(2)
        assertEquals(-1L, backoff)
    }
}
