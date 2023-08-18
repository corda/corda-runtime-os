package net.corda.membership.client.dto

/**
 * Data class used to hold the properties that were forwarded to the MGM by a member during registration.
 *
 * @param data Information sent to the MGM for registration.
 */
data class MemberInfoSubmittedDto(
    val data: Map<String, String>
)
