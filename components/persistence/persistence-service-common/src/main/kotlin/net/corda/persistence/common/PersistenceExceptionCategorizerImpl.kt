package net.corda.persistence.common

import net.corda.persistence.common.PersistenceExceptionType.FATAL
import net.corda.persistence.common.PersistenceExceptionType.PLATFORM
import net.corda.persistence.common.PersistenceExceptionType.TRANSIENT
import org.hibernate.ResourceClosedException
import org.hibernate.SessionException
import org.hibernate.TransactionException
import org.hibernate.cache.CacheException
import org.hibernate.exception.JDBCConnectionException
import org.hibernate.exception.LockAcquisitionException
import org.slf4j.LoggerFactory
import java.sql.SQLTransientConnectionException
import javax.persistence.LockTimeoutException
import javax.persistence.OptimisticLockException
import javax.persistence.PessimisticLockException
import javax.persistence.QueryTimeoutException
import javax.persistence.RollbackException
import javax.persistence.TransactionRequiredException

internal class PersistenceExceptionCategorizerImpl : PersistenceExceptionCategorizer {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun categorize(exception: Exception): PersistenceExceptionType {
        return when {
            isTransient(exception) -> TRANSIENT
            isFatal(exception) -> FATAL
            else -> PLATFORM
        }.also {
            logger.warn("Categorized exception as $it: $exception", exception)
        }
    }

    private fun isFatal(exception: Exception): Boolean {
        return when (exception) {
            // [PersistenceException]s
            is TransactionRequiredException, is ResourceClosedException, is SessionException -> true
            else -> false
        }
    }

    private fun isTransient(exception: Exception): Boolean {
        return when (exception) {
            // [PersistenceException]s
            is LockTimeoutException,
            is OptimisticLockException,
            is PessimisticLockException,
            is QueryTimeoutException,
            is RollbackException,
            // [JDBCException]s
            is org.hibernate.PessimisticLockException,
            is org.hibernate.QueryTimeoutException,
            is JDBCConnectionException,
            is LockAcquisitionException,
            // [HibernateException]s
            is TransactionException,
            is CacheException -> true
            // Exception thrown by Hikari
            is SQLTransientConnectionException -> exception.message?.lowercase()?.contains("connection is not available") == true
            else -> false
        }
    }
/*
    @Suppress("UNCHECKED_CAST")
    private fun <T : Throwable> Throwable?.meetsCriteria(
        exceptionType: Class<T>,
        check: (T) -> Boolean = { _ -> true }
    ): Boolean {
        if (this == null) {
            return false
        }
        val meetsCriteria = if (exceptionType.isAssignableFrom(this::class.java)) {
            val param = this as? T
            param?.let { check(it) } ?: false
        } else {
            false
        }
        return (meetsCriteria || cause.meetsCriteria(exceptionType, check))
    }
    */
}