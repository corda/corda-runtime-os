package net.corda.membership.persistence.client

/**
 * Data class representing the result of a membership query operation.
 * @param payload The result of the query operation. Can vary depending on the operation and can be null if no
 *  result was returned.
 * @param errorMsg If there was an error during persistence this field is non-null. Otherwise it is null.
 */
data class MembershipQueryResult<T>(
    val payload: T? = null,
    val errorMsg: String? = null
)