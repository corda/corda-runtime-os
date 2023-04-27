package net.corda.crypto.core

import net.corda.v5.crypto.exceptions.CryptoException
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
        // AbstractComponentNotReadyException is thrown when lifecycle is goes away from up on an abstract component, 
        // e.g. due to a transient or permanent error; we retry since it may come up later, and if it stays down
        // long enough we will get restarted and this code may have more luck in the new process
        is AbstractComponentNotReadyException -> true
        is TimeoutException -> true
        is CryptoException -> isRecoverable
        else -> when {
            RETRYABLE_EXCEPTIONS.contains(this::class.java.name) -> true
            cause != null -> cause!!.isRecoverable()
            else -> false
        }
    }