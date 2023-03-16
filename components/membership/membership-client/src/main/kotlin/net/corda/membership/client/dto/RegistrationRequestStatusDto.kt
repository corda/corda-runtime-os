package net.corda.membership.client.dto

import java.time.Instant

/**
 * Data class representing a status of a member's registration.
 *
 * @param registrationId The registration request ID.
 * @param registrationSent Date when registration started. Null if not submitted.
 * @param registrationUpdated Date when registration was updated.
 * @param registrationStatus Status of registration request.
 * @param memberInfoSubmitted Information sent to the MGM for registration.
 * @param reason Reason why the request is in the status specified by [registrationStatus].
 */
data class RegistrationRequestStatusDto(
    val registrationId: String,
    val registrationSent: Instant?,
    val registrationUpdated: Instant?,
    val registrationStatus: RegistrationStatusDto,
    val memberInfoSubmitted: MemberInfoSubmittedDto,
    val reason: String? = null,
)
