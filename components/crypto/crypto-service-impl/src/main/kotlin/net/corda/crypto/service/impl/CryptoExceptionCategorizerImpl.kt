package net.corda.crypto.service.impl

import net.corda.crypto.core.isRecoverable
import net.corda.crypto.service.CryptoExceptionCategorizer
import net.corda.crypto.service.CryptoExceptionType
import net.corda.crypto.service.CryptoExceptionType.FATAL
import net.corda.crypto.service.CryptoExceptionType.PLATFORM
import net.corda.crypto.service.CryptoExceptionType.TRANSIENT
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.flow.external.events.responses.exceptions.criteria
import net.corda.v5.crypto.exceptions.CryptoException
import org.hibernate.exception.JDBCConnectionException
import org.hibernate.exception.LockAcquisitionException
import org.slf4j.LoggerFactory
import java.sql.SQLTransientException
import javax.persistence.LockTimeoutException
import javax.persistence.OptimisticLockException
import javax.persistence.QueryTimeoutException

internal class CryptoExceptionCategorizerImpl : CryptoExceptionCategorizer {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun categorize(exception: Exception): CryptoExceptionType {
        return when {
            isFatal(exception) -> FATAL
            isPlatform(exception) -> PLATFORM
            isTransient(exception) -> TRANSIENT
            else -> PLATFORM
        }.also {
            logger.warn("Categorized exception as $it: $exception", exception)
        }
    }

    private fun isFatal(exception: Exception): Boolean {
        val checks = listOf(
            criteria<DBConfigurationException>(),
        )

        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isPlatform(exception: Exception): Boolean {
        val checks = listOf(
            criteria<IllegalArgumentException>(),
            criteria<IllegalStateException>(),
            criteria<CryptoException> {
                !exception.isRecoverable()
            }
        )

        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isTransient(exception: Exception): Boolean {
        val checks = listOf(
            criteria<SQLTransientException>(),
            criteria<JDBCConnectionException>(),
            criteria<LockAcquisitionException>(),
            criteria<LockTimeoutException>(),
            criteria<QueryTimeoutException>(),
            criteria<OptimisticLockException>(),
            criteria<CryptoException> {
                exception.isRecoverable()
            },

        )

        return checks.any { it.meetsCriteria(exception) }
    }
}
