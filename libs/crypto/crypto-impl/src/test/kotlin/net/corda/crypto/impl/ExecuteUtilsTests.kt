package net.corda.crypto.impl

import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.exceptions.CryptoException
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
            CryptoException("error"),
            CryptoException(
                "error",
                CryptoException("error", true)
            ),
            CryptoException(
                "error",
                TimeoutException()
            ),
            PersistenceException()
        )

        @JvmStatic
        fun retryableExceptions(): List<Throwable> = listOf(
            CryptoException("error", true),
            TimeoutException(),
            LockTimeoutException(),
            QueryTimeoutException(),
            OptimisticLockException(),
            PessimisticLockException(),
            java.sql.SQLTransientException(),
            java.sql.SQLTimeoutException(),
            org.hibernate.exception.LockAcquisitionException("error", java.sql.SQLException()),
            org.hibernate.exception.LockTimeoutException("error", java.sql.SQLException()),
            RuntimeException("error", TimeoutException()),
            PersistenceException("error", LockTimeoutException()),
            PersistenceException("error", QueryTimeoutException()),
            PersistenceException("error", OptimisticLockException()),
            PersistenceException("error", PessimisticLockException()),
            PersistenceException("error", java.sql.SQLTransientException()),
            PersistenceException("error", java.sql.SQLTimeoutException()),
            PersistenceException("error", org.hibernate.exception.LockAcquisitionException(
                "error", java.sql.SQLException()
            )),
            PersistenceException("error", org.hibernate.exception.LockTimeoutException(
                "error", java.sql.SQLException()
            ))
        )
    }

    @Test
    fun `Should execute without retrying`() {
        var called = 0
        val result = CryptoRetryingExecutor(logger, 3).executeWithRetry {
            called++
            "Hello World!"
        }
        assertEquals("Hello World!", result)
        assertEquals(1, called)
    }

    @Test
    fun `Should execute withTimeout without retrying`() {
        var called = 0
        val result = CryptoRetryingExecutorWithTimeout(logger, 3, Duration.ofSeconds(5)).executeWithRetry {
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
            CryptoRetryingExecutorWithTimeout(logger, 1, Duration.ofMillis(10)).executeWithRetry {
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
            CryptoRetryingExecutorWithTimeout(logger, 3, Duration.ofMillis(10)).executeWithRetry {
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
        assertThrows<CryptoException> {
            CryptoRetryingExecutorWithTimeout(logger, 3, Duration.ofMillis(10)).executeWithRetry {
                called++
                throw CryptoException("error")
            }
        }
        assertEquals(1, called)
    }

    @Test
    fun `Should eventually fail with retrying`() {
        var called = 0
        assertThrows<TimeoutException> {
            CryptoRetryingExecutor(logger, 3, Duration.ofMillis(10)).executeWithRetry {
                called++
                throw TimeoutException()
            }
        }
        assertEquals(3, called)
    }

    @Test
    fun `Should eventually succeed after retrying TimeoutException`() {
        var called = 0
        val result = CryptoRetryingExecutor(logger, 3, Duration.ofMillis(10)).executeWithRetry {
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
        val result = CryptoRetryingExecutor(logger, 3, Duration.ofMillis(10)).executeWithRetry {
            called++
            if (called <= 2) {
                throw CryptoException("error", true)
            }
            "Hello World!"
        }
        assertEquals("Hello World!", result)
        assertEquals(3, called)
    }

    @Test
    fun `Should eventually succeed after retrying recoverable crypto library exception`() {
        var called = 0
        val result = CryptoRetryingExecutor(logger, 3, Duration.ofMillis(10)).executeWithRetry {
            called++
            if (called <= 2) {
                throw CryptoException("error", true)
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
        val result = CryptoRetryingExecutor(logger, 2, Duration.ofMillis(10)).executeWithRetry {
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