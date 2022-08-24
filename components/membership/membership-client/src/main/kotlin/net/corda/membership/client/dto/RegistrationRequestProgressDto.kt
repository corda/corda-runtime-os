package net.corda.membership.client.dto

import java.time.Instant

/**
 * Data class representing the latest known status of a member's registration.
 *
 * @param registrationRequestId The request ID
 * @param registrationSent Date when registration progress started. Null if not submitted.
 * @param registrationStatus Status of registration request: Submitted or not submitted.
 * @param memberInfoSubmitted Information sent to the MGM for registration.
 */
data class RegistrationRequestProgressDto(
    val registrationRequestId: String,
    val registrationSent: Instant?,
    val registrationStatus: String,
    val memberInfoSubmitted: MemberInfoSubmittedDto
)
