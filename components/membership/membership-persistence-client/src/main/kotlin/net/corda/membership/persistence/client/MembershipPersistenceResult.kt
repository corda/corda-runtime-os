package net.corda.membership.persistence.client

import net.corda.data.membership.db.response.query.ErrorKind
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException

sealed class MembershipPersistenceResult<T> {
    companion object {
        /**
         * Creates a new Success object with empty response
         */
        fun success(): MembershipPersistenceResult<Unit> {
            return Success(Unit)
        }
    }

    /**
     * Data class representing the successful result of a membership persistence operation.
     *
     * @param payload The result of the persistence operation. Can vary depending on the operation and can be null if no
     *  result was returned.
     */
    data class Success<T>(val payload: T) : MembershipPersistenceResult<T>()

    /**
     * Data class representing the result of a failed membership persistence operation.
     * @param errorMsg Information regarding the error which occurred.
     * @param kind The error kind.
     */
    data class Failure<T>(val errorMsg: String, val kind: ErrorKind = ErrorKind.GENERAL) : MembershipPersistenceResult<T>()

    /**
     * An exception in the persistence request
     */
    class PersistenceRequestException(failure: Failure<*>) : MembershipPersistenceClientException(failure.errorMsg)

    /**
     * Return the value or throw an exception if the persistence failed.
     */
    fun getOrThrow(): T {
        return when (this) {
            is Success -> this.payload
            is Failure -> when (this.kind) {
                ErrorKind.INVALID_ENTITY_UPDATE -> throw InvalidEntityUpdateException(this.errorMsg)
                ErrorKind.GENERAL -> throw PersistenceRequestException(this)
            }
        }
    }
}
