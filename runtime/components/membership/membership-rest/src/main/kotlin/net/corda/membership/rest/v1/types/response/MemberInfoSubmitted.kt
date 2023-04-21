package net.corda.membership.rest.v1.types.response

/**
 * Data class used to hold the properties that were forwarded to the MGM by a member during registration.
 *
 * @param data Information sent to the MGM for registration.
 */
data class MemberInfoSubmitted(
    val data: Map<String, String>
)
