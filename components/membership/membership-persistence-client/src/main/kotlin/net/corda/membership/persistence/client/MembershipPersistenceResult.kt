package net.corda.membership.persistence.client

/**
 * Data class representing the result of a membership persistence operation.
 * @param payload The result of the persistence operation. Can vary depending on the operation and can be null if no
 *  result was returned.
 * @param errorMsg If there was an error during persistence this field is non-null. Otherwise it is null.
 */
data class MembershipPersistenceResult(
    val payload: Any? = null,
    val errorMsg: String? = null
)