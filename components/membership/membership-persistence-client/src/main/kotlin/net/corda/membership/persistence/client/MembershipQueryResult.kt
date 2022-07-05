package net.corda.membership.persistence.client

import net.corda.v5.base.exceptions.CordaRuntimeException

sealed class MembershipQueryResult<T> {
    /**
     * Data class representing the successful result of a membership query operation.
     * @param payload The result of the query operation. Can vary depending on the operation and can be null if no
     *  result was returned.
     */
    data class Success<T>(val payload: T) : MembershipQueryResult<T>()
    /**
     * Data class representing the result of a failed membership query operation.
     * @param errorMsg If there was an error during persistence this field is the message for that error.
     */
    data class Failure<T>(val errorMsg: String) : MembershipQueryResult<T>()

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
