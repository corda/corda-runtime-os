package net.corda.db.core.utils

import org.slf4j.LoggerFactory
import java.sql.Connection

private val log = LoggerFactory.getLogger("ConnectionUtils")

/**
 * Executes [block] in a transaction using the [Connection].
 *
 * Commits transaction if no exceptions were thrown by [block]. Otherwise, rolls back the transaction.
 *
 * Finally closes the connection after committing or rolling back the changes.
 *
 * @param block The code to execute before committing the transaction.
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 */
fun <R> Connection.transaction(block: (Connection) -> R): R {
    return transactionWithLogging(null, block)
}

fun <R> Connection.transactionWithLogging(name: String?, block: (Connection) -> R): R {
    if(null != name && log.isTraceEnabled) log.trace("Start transaction $name")
    autoCommit = false
    return try {
        block(this).also {
            commit()
            if(null != name && log.isTraceEnabled) log.trace("Transaction $name committed")
        }
    } catch (e: Exception) {
        rollback()
        if(null != name && log.isWarnEnabled) log.error("Transaction $name rolled back")
        throw e
    } finally {
        close()
    }
}
