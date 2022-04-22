package net.corda.membership.persistence.client

data class MembershipPersistenceResult(
    val success: Boolean,
    val payload: Any? = null,
    val errorMsg: String? = null
)