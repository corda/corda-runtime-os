package net.corda.crypto.core

import net.corda.v5.crypto.failures.CryptoException
import java.util.concurrent.TimeoutException

// don't want to depend here on the JPA directly hence the class names
val RETRYABLE_EXCEPTIONS = setOf(
    "javax.persistence.LockTimeoutException",
    "javax.persistence.QueryTimeoutException",
    "javax.persistence.OptimisticLockException",
    "javax.persistence.PessimisticLockException",
    "java.sql.SQLTransientException",
    "java.sql.SQLTimeoutException",
    "org.hibernate.exception.LockAcquisitionException",
    "org.hibernate.exception.LockTimeoutException"
)

fun Throwable.isRecoverable(): Boolean =
    when (this) {
        is TimeoutException -> true
        is CryptoException -> isRecoverable
        else -> when {
            RETRYABLE_EXCEPTIONS.contains(this::class.java.name) -> true
            cause != null -> cause!!.isRecoverable()
            else -> false
        }
    }