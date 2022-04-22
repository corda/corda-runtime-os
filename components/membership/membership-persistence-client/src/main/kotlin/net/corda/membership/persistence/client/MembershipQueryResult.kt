package net.corda.membership.persistence.client

data class MembershipQueryResult<T>(
    val success: Boolean,
    val payload: T? = null,
    val errorMsg: String? = null
)