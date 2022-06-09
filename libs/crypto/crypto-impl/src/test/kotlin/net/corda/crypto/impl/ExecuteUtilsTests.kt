package net.corda.crypto.impl

import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.util.concurrent.TimeoutException
import javax.persistence.LockTimeoutException
import javax.persistence.OptimisticLockException
import javax.persistence.PersistenceException
import javax.persistence.PessimisticLockException
import javax.persistence.QueryTimeoutException
import kotlin.test.assertEquals

class ExecuteUtilsTests {
    companion object {
        private val logger = contextLogger()

        @JvmStatic
        fun mostCommonUnrecoverableExceptions(): List<Throwable> = listOf(
            IllegalStateException(),
            IllegalArgumentException(),
            NullPointerException(),
            IndexOutOfBoundsException(),
            NoSuchElementException(),
            RuntimeException(),
            ClassCastException(),
            NotImplementedError(),
            UnsupportedOperationException(),
            CryptoServiceLibraryException("error", isRecoverable = false),
            CryptoServiceLibraryException(
                "error",
                CryptoServiceLibraryException("error", isRecoverable = true),
                isRecoverable = false
            ),
            CryptoServiceLibraryException(
                "error",
                TimeoutException(),
                isRecoverable = false
            ),
            PersistenceException()
        )

        @JvmStatic
        fun retryableExceptions(): List<Throwable> = listOf(
            CryptoServiceLibraryException("error", isRecoverable = true),
            TimeoutException(),
            LockTimeoutException(),
            QueryTimeoutException(),
            OptimisticLockException(),
            PessimisticLockException(),
            RuntimeException("error", TimeoutException()),
            PersistenceException("error", LockTimeoutException()),
            PersistenceException("error", QueryTimeoutException()),
            PersistenceException("error", OptimisticLockException()),
            PersistenceException("error", PessimisticLockException())
        )
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

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `WithTimeout should not retry common exceptions`(e: Throwable) {
        var called = 0
        val actual = assertThrows<Throwable> {
            ExecutorWithTimeout(logger, 3, Duration.ofMillis(10)).executeWithRetry {
                called++
                throw e
            }
        }
        assertEquals(e::class.java, actual::class.java)
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
    fun `Should eventually fail with retrying`() {
        var called = 0
        assertThrows<TimeoutException> {
            Executor(logger, 3, Duration.ofMillis(10)).executeWithRetry {
                called++
                throw TimeoutException()
            }
        }
        assertEquals(3, called)
    }

    @Test
    fun `Should eventually succeed after retrying TimeoutException`() {
        var called = 0
        val result = Executor(logger, 3, Duration.ofMillis(10)).executeWithRetry {
            called++
            if (called <= 2) {
                throw TimeoutException()
            }
            "Hello World!"
        }
        assertEquals("Hello World!", result)
        assertEquals(3, called)
    }

    @Test
    fun `Should eventually succeed after retrying recoverable CryptoServiceLibraryException`() {
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

    @Test
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

    @ParameterizedTest
    @MethodSource("retryableExceptions")
    fun `Should retry all retryable exceptions`(e: Throwable) {
        var called = 0
        val result = Executor(logger, 2, Duration.ofMillis(10)).executeWithRetry {
            called++
            if (called < 2) {
                throw e
            }
            "Hello World!"
        }
        assertEquals("Hello World!", result)
        assertEquals(2, called)
    }
}