package net.corda.membership.persistence.client

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
     * An exception in the query
     */
    class QueryException(failure: Failure<*>) : MembershipPersistenceClientException(failure.errorMsg)

    /**
     * Return the value or throw an exception if the persistence failed.
     */
    fun getOrThrow(): T {
        return when (this) {
            is Success -> this.payload
            is Failure -> throw QueryException(this)
        }
    }
}
