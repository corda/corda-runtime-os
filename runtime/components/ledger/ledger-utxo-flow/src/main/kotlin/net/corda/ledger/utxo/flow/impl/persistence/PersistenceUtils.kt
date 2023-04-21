package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Catch a [CordaRuntimeException] thrown by [function] and rethrow as a [CordaPersistenceException].
 *
 * @param function The function to execute.
 * @param T The type to return.
 *
 * @return [T]
 *
 * @throws CordaPersistenceException When a [CordaRuntimeException] is thrown, it is caught and rethrown as a
 * [CordaPersistenceException].
 */
inline fun <T> wrapWithPersistenceException(function: () -> T): T {
    return try {
        function()
    } catch (e: CordaRuntimeException) {
        throw CordaPersistenceException(e.message ?: "Exception occurred when executing persistence operation")
    }
}
