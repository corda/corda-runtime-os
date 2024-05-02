package net.corda.orm.impl

import net.corda.orm.PersistenceExceptionCategorizer
import net.corda.orm.PersistenceExceptionType
import net.corda.orm.PersistenceExceptionType.DATA_RELATED
import net.corda.orm.PersistenceExceptionType.FATAL
import net.corda.orm.PersistenceExceptionType.TRANSIENT
import net.corda.orm.PersistenceExceptionType.UNCATEGORIZED
import net.corda.utilities.criteria
import org.hibernate.QueryException
import org.hibernate.ResourceClosedException
import org.hibernate.SessionException
import org.hibernate.TransactionException
import org.hibernate.cache.CacheException
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.GenericJDBCException
import org.hibernate.exception.JDBCConnectionException
import org.hibernate.exception.LockAcquisitionException
import org.hibernate.exception.SQLGrammarException
import org.hibernate.procedure.NoSuchParameterException
import org.hibernate.procedure.ParameterMisuseException
import org.hibernate.property.access.spi.PropertyAccessException
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import javax.persistence.EntityExistsException
import javax.persistence.EntityNotFoundException
import javax.persistence.LockTimeoutException
import javax.persistence.NonUniqueResultException
import javax.persistence.OptimisticLockException
import javax.persistence.PessimisticLockException
import javax.persistence.QueryTimeoutException
import javax.persistence.RollbackException
import javax.persistence.TransactionRequiredException

@Component(service = [PersistenceExceptionCategorizer::class])
class PersistenceExceptionCategorizerImpl : PersistenceExceptionCategorizer {

    companion object {
        internal const val CONNECTION_CLOSED_MESSAGE = "Connection is closed"
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun categorize(exception: Exception): PersistenceExceptionType {
        return when {
            isFatal(exception) -> FATAL
            isDataRelated(exception) -> DATA_RELATED
            isTransient(exception) -> TRANSIENT
            else -> UNCATEGORIZED
        }.also {
            logger.warn("Categorized exception as $it: $exception", exception)
        }
    }

    private fun isFatal(exception: Exception): Boolean {
        val checks = listOf(
            criteria<TransactionRequiredException>(),
            criteria<ResourceClosedException>(),
            criteria<SessionException>(),
        )
        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isDataRelated(exception: Exception): Boolean {
        val checks = listOf(
            criteria<EntityExistsException>(),
            criteria<EntityNotFoundException>(),
            criteria<NonUniqueResultException>(),
            criteria<SQLGrammarException>(),
            criteria<GenericJDBCException>(),
            criteria<QueryException>(),
            criteria<NoSuchParameterException>(),
            criteria<ParameterMisuseException>(),
            criteria<PropertyAccessException>(),
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
                it.sqlState in setOf("08001", "08003", "08004", "08006", "08007", "58030")
            },
            criteria<SQLException> {
                it.message == CONNECTION_CLOSED_MESSAGE
            },
            criteria<SocketException>()
        )
        return checks.any { it.meetsCriteria(exception) }
    }
}