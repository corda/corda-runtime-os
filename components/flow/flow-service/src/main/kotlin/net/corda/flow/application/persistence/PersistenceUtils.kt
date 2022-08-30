package net.corda.flow.application.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Catch a CordaRuntimeException thrown by a [function] and rethrow as a [CordaPersistenceException]
 */
@Suspendable
inline fun <T>  wrapWithPersistenceException(function: () -> T): T {
    return try {
        function()
    } catch (e: CordaRuntimeException) {
        throw CordaPersistenceException(e.message ?: "Exception occurred when executing persistence operation")
    }
}
