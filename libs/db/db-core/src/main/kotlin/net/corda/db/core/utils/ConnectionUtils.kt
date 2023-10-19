package net.corda.db.core.utils

import java.sql.Connection

/**
 * Executes [block] in a transaction using the [Connection].
 *
 * Commits transaction if no exceptions were thrown by [block]. Otherwise rolls back the transaction.
 *
 * Finally closes the connection after committing or rolling back the changes.
 *
 * @param block The code to execute before committing the transaction.
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 */
inline fun <R> Connection.transaction(block: (Connection) -> R): R {
    autoCommit = false // Start a transaction
    return try {
        block(this).also { commit() }
    } catch (e: Exception) {
        rollback()
        throw e
    } finally {
        close()
    }
}
