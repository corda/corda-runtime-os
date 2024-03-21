package net.corda.persistence.common

import net.corda.persistence.common.PersistenceExceptionType.FATAL
import net.corda.persistence.common.PersistenceExceptionType.PLATFORM
import net.corda.persistence.common.PersistenceExceptionType.TRANSIENT
import org.hibernate.ResourceClosedException
import org.hibernate.SessionException
import org.hibernate.TransactionException
import org.hibernate.cache.CacheException
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.JDBCConnectionException
import org.hibernate.exception.LockAcquisitionException
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.sql.SQLException
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
            isFatal(exception) -> FATAL
            isPlatform(exception) -> PLATFORM
            isTransient(exception) -> TRANSIENT
            else -> PLATFORM
        }.also {
            logger.warn("Categorized exception as $it: $exception", exception)
        }
    }

    private data class ExceptionCriteria<T: Throwable>(val type: Class<T>, val check: (T) -> Boolean = { _ -> true }) {
        fun meetsCriteria(exception: Throwable?) : Boolean {
            if (exception == null) {
                return false
            }
            val meetsCriteria = if (type.isAssignableFrom(exception::class.java)) {
                check(type.cast(exception))
            } else {
                false
            }
            return (meetsCriteria || meetsCriteria(exception.cause))
        }
    }

    private inline fun <reified T: Throwable> criteria(
        noinline check: (T) -> Boolean = { _ -> true }
    ) : ExceptionCriteria<T> = ExceptionCriteria(T::class.java, check)

    private fun isFatal(exception: Exception): Boolean {
        val checks = listOf(
            criteria<TransactionRequiredException>(),
            criteria<ResourceClosedException>(),
            criteria<SessionException>(),
        )
        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isPlatform(exception: Exception): Boolean {
        val checks = listOf(
            criteria<ConstraintViolationException>()
        )
        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isTransient(exception: Exception): Boolean {
        val checks = listOf(
            criteria<LockTimeoutException>(),
            criteria<OptimisticLockException>(),
            criteria<PessimisticLockException>(),
            criteria<QueryTimeoutException>(),
            criteria<RollbackException>(),
            criteria<org.hibernate.PessimisticLockException>(),
            criteria<org.hibernate.QueryTimeoutException>(),
            criteria<JDBCConnectionException>(),
            criteria<LockAcquisitionException>(),
            criteria<TransactionException>(),
            criteria<CacheException>(),
            criteria<SQLTransientConnectionException> {
                exception.message?.lowercase()?.contains("connection is not available") == true
            },
            criteria<SQLException> {
                it.sqlState in setOf("08001", "08003", "08004", "08006", "08006", "58030")
            },
            criteria<SQLException> {
                it.message == "Connection is closed"
            },
            criteria<SocketException>()
        )
        return checks.any { it.meetsCriteria(exception) }
/*        return when (exception) {
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
        }*/
    }
}