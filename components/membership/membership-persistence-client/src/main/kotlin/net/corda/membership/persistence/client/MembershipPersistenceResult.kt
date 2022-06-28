package net.corda.membership.persistence.client

sealed class MembershipPersistenceResult<T> {
    /**
     * Data class representing the successful result of a membership persistence operation.
     *
     * @param payload The result of the persistence operation. Can vary depending on the operation and can be null if no
     *  result was returned.
     */
    data class Success<T>(val payload: T? = null) : MembershipPersistenceResult<T>()

    /**
     * Data class representing the result of a failed membership persistence operation.
     * @param errorMsg Information regarding the error which occurred.
     */
    data class Failure<T>(val errorMsg: String) : MembershipPersistenceResult<T>()
}