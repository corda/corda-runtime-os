package net.corda.crypto.impl

import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.test.assertEquals

class ExecuteUtilsTests {
    companion object {
        private val logger = contextLogger()
    }

    @Test
    fun `Should execute without retrying`() {
        var called = 0
        val result = executeWithRetry(logger, 3) {
            called++
            "Hello World!"
        }
        assertEquals("Hello World!", result)
        assertEquals(1, called)
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `Should eventually fail with retrying`() {
        var called = 0
        assertThrows<RuntimeException> {
            executeWithRetry(logger, 3, Duration.ofMillis(10)) {
                called++
                throw RuntimeException()
            }
        }
        assertEquals(3, called)
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `Should eventually succeed after retrying`() {
        var called = 0
        val result = executeWithRetry(logger, 3, Duration.ofMillis(10)) {
            called++
            if (called <= 2) {
                throw RuntimeException()
            }
            "Hello World!"
        }
        assertEquals("Hello World!", result)
        assertEquals(3, called)
    }
}