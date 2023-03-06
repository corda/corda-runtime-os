package net.corda.crypto.core

import net.corda.v5.crypto.exceptions.CryptoException
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.security.SignatureException
import java.util.concurrent.TimeoutException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CryptoExceptionsUtilsTests {
    companion object {
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
            CryptoRetryException("error", TimeoutException()),
            CryptoSignatureException("error", SignatureException()),
            javax.persistence.PersistenceException()
        )

        @JvmStatic
        fun allRecoverableExceptions(): List<Throwable> = listOf(
            CryptoException("error", true),
            TimeoutException(),
            javax.persistence.LockTimeoutException(),
            javax.persistence.QueryTimeoutException(),
            javax.persistence.OptimisticLockException(),
            javax.persistence.PessimisticLockException(),
            java.sql.SQLTransientException(),
            java.sql.SQLTimeoutException(),
            org.hibernate.exception.LockAcquisitionException("error", java.sql.SQLException()),
            org.hibernate.exception.LockTimeoutException("error", java.sql.SQLException()),
            RuntimeException("error", TimeoutException()),
            javax.persistence.PersistenceException("error", javax.persistence.LockTimeoutException()),
            javax.persistence.PersistenceException("error", javax.persistence.QueryTimeoutException()),
            javax.persistence.PersistenceException("error", javax.persistence.OptimisticLockException()),
            javax.persistence.PersistenceException("error", javax.persistence.PessimisticLockException()),
            javax.persistence.PersistenceException("error", java.sql.SQLTransientException()),
            javax.persistence.PersistenceException("error", java.sql.SQLTimeoutException()),
            javax.persistence.PersistenceException("error", org.hibernate.exception.LockAcquisitionException(
                "error", java.sql.SQLException()
            )
            ),
            javax.persistence.PersistenceException("error", org.hibernate.exception.LockTimeoutException(
                "error", java.sql.SQLException()
            ))
        )
    }

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `isRecoverable should return false for non recoverable exceptions`(e: Throwable) {
        assertFalse(e.isRecoverable())

    }

    @ParameterizedTest
    @MethodSource("allRecoverableExceptions")
    fun `isRecoverable should return true for all recoverable exceptions`(e: Throwable) {
        assertTrue(e.isRecoverable())
    }
}