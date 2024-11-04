package net.corda.ledger.libs.uniqueness.backingstore.impl

import net.corda.db.core.PersistenceExceptionCategorizer
import net.corda.db.core.PersistenceExceptionType
import net.corda.ledger.libs.uniqueness.backingstore.ConsumeStateFailedException
import net.corda.utilities.criteria
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.sql.SQLException
import java.sql.SQLTransientConnectionException

class SqlPersistenceExceptionCategorizerImpl : PersistenceExceptionCategorizer {

    companion object {
        internal const val CONNECTION_CLOSED_MESSAGE = "Connection is closed"
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun categorize(exception: Exception): PersistenceExceptionType {
        return when {
            isFatal(exception) -> PersistenceExceptionType.FATAL
            isDataRelated(exception) -> PersistenceExceptionType.DATA_RELATED
            isTransient(exception) -> PersistenceExceptionType.TRANSIENT
            else -> PersistenceExceptionType.UNCATEGORIZED
        }.also {
            logger.warn("Categorized exception as $it: $exception", exception)
        }
    }

    // list of sqlSate codes: https://github.com/spring-projects/spring-framework/blob/main/spring-jdbc/src/main/resources/org/springframework/jdbc/support/sql-error-codes.xml
    private fun isFatal(exception: Exception): Boolean {
        val checks = listOf(
            criteria<SQLException> {
                it.sqlState in setOf(
                    // badSqlGrammarCodes
                    "03000", "42000", "42601", "42602", "42622", "42804", "42P01",
                    // incorrect field
                    "42703"
                )
            },
        )
        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isDataRelated(exception: Exception): Boolean {
        val checks = listOf(
            criteria<ConsumeStateFailedException>(),
            criteria<SQLException> {
                it.sqlState in setOf(
                    // duplicateKeyCodes
                    "21000", "23505",
                    // dataIntegrityViolationCodes
                    "23000", "23502", "23503", "23514",
                )
            },
        )
        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isTransient(exception: Exception): Boolean {
        val checks = listOf(
            criteria<SQLTransientConnectionException> {
                exception.message?.lowercase()?.contains("connection is not available") == true
            },
            criteria<SQLException> {
                it.sqlState in setOf(
                    // dataAccessResourceFailureCodes
                    "53000", "53100", "53200", "53300",
                    // cannotAcquireLockCodes
                    "55P03",
                    // deadlockLoserCodes
                    "40P01",
                    // unsure when these happen (source unknown)
                    "08001", "08003", "08004", "08006", "08007", "58030",
                )
            },
            criteria<SQLException> {
                it.message?.contains(CONNECTION_CLOSED_MESSAGE) ?: false
            },
            criteria<SocketException>()
        )
        return checks.any { it.meetsCriteria(exception) }
    }
}
