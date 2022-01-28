package net.corda.membership.httprpc.types

data class MemberInfoSubmitted(
    /** Information sent to the MGM for registration. */
    val data: Map<String, String>
)
