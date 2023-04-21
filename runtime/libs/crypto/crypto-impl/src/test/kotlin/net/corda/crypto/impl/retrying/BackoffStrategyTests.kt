package net.corda.crypto.impl.retrying

import net.corda.crypto.impl.retrying.BackoffStrategy.Companion.createBackoff
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackoffStrategyTests {
    @Test
    fun `Should return customizes backoff`() {
        val strategy =  createBackoff(3, listOf(100, 200))
        var backoff = strategy.getBackoff(1)
        assertEquals(100L, backoff)
        backoff = strategy.getBackoff(2)
        assertEquals(200L, backoff)
        backoff = strategy.getBackoff(3)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return customizes backoff with repeating value`() {
        val strategy = createBackoff(3, listOf(300))
        var backoff = strategy.getBackoff(1)
        assertEquals(300L, backoff)
        backoff = strategy.getBackoff(2)
        assertEquals(300L, backoff)
        backoff = strategy.getBackoff(3)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return customizes backoff for attempts 0 and empty list`() {
        val strategy =  createBackoff(0, emptyList())
        val backoff = strategy.getBackoff(1)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return customizes backoff for attempts 1 and empty list`() {
        val strategy =  createBackoff(1, emptyList())
        val backoff = strategy.getBackoff(1)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return customizes backoff for attempts 2 and empty list`() {
        val strategy =  createBackoff(2, emptyList())
        var backoff = strategy.getBackoff(1)
        assertEquals(0L, backoff)
        backoff = strategy.getBackoff(2)
        assertEquals(-1L, backoff)
    }
}