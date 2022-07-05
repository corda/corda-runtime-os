package net.corda.membership.persistence.client

import net.corda.v5.base.exceptions.CordaRuntimeException

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
     */
    data class Failure<T>(val errorMsg: String) : MembershipPersistenceResult<T>()

    /**
     * Return the value or throw an exception if the persistence failed.
     */
    fun getOrThrow(): T {
        return when (this) {
            is Success -> this.payload
            is Failure -> throw CordaRuntimeException(this.errorMsg)
        }
    }
}
