package net.corda.crypto.impl

import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals

class ExecuteUtilsTests {
    companion object {
        private val logger = contextLogger()
    }

    @Test
    fun `Should execute without retrying`() {
        var called = 0
        val result = Executor(logger, 3).executeWithRetry {
            called++
            "Hello World!"
        }
        assertEquals("Hello World!", result)
        assertEquals(1, called)
    }

    @Test
    fun `Should execute withTimeout without retrying`() {
        var called = 0
        val result = ExecutorWithTimeout(logger, 3, Duration.ofSeconds(5)).executeWithRetry {
            called++
            "Hello World!"
        }
        assertEquals("Hello World!", result)
        assertEquals(1, called)
    }

    @Test
    fun `WithTimeout should throw TimeoutException`() {
        var called = 0
        assertThrows<TimeoutException> {
            ExecutorWithTimeout(logger, 1, Duration.ofMillis(10)).executeWithRetry {
                called++
                Thread.sleep(100)
            }
        }
        assertEquals(1, called)
    }

    @Test
    fun `WithTimeout should not retry IllegalArgumentException`() {
        var called = 0
        assertThrows<IllegalArgumentException> {
            ExecutorWithTimeout(logger, 3, Duration.ofMillis(10)).executeWithRetry {
                called++
                throw IllegalArgumentException()
            }
        }
        assertEquals(1, called)
    }

    @Test
    fun `WithTimeout should not retry unrecoverable crypto library exception`() {
        var called = 0
        assertThrows<CryptoServiceLibraryException> {
            ExecutorWithTimeout(logger, 3, Duration.ofMillis(10)).executeWithRetry {
                called++
                throw CryptoServiceLibraryException("error", isRecoverable = false)
            }
        }
        assertEquals(1, called)
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `Should eventually fail with retrying`() {
        var called = 0
        assertThrows<IllegalStateException> {
            Executor(logger, 3, Duration.ofMillis(10)).executeWithRetry {
                called++
                throw IllegalStateException()
            }
        }
        assertEquals(3, called)
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `Should eventually succeed after retrying`() {
        var called = 0
        val result = Executor(logger, 3, Duration.ofMillis(10)).executeWithRetry {
            called++
            if (called <= 2) {
                throw IllegalStateException()
            }
            "Hello World!"
        }
        assertEquals("Hello World!", result)
        assertEquals(3, called)
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `Should eventually succeed after retrying recoverable crypto library exception`() {
        var called = 0
        val result = Executor(logger, 3, Duration.ofMillis(10)).executeWithRetry {
            called++
            if (called <= 2) {
                throw CryptoServiceLibraryException("error", isRecoverable = true)
            }
            "Hello World!"
        }
        assertEquals("Hello World!", result)
        assertEquals(3, called)
    }
}