package net.corda.membership.rest.v1.types.response

import java.time.Instant

/**
 * Data class representing a status of a member's registration.
 *
 * @param registrationId The registration request ID.
 * @param registrationSent Date when registration started.
 * @param registrationUpdated Date when registration was updated.
 * @param registrationStatus Status of registration request.
 * @param memberInfoSubmitted Information sent to the MGM for registration.
 * @param reason Reason why the request is in the status specified by [registrationStatus].
 * @param serial The version the member wanted to update with their request. 0 for first time registrations.
 */
data class RestRegistrationRequestStatus(
    val registrationId: String,
    val registrationSent: Instant?,
    val registrationUpdated: Instant?,
    val registrationStatus: RegistrationStatus,
    val memberInfoSubmitted: MemberInfoSubmitted,
    val reason: String?,
    val serial: Long?,
)
