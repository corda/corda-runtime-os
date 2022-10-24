package net.corda.membership.httprpc.v1.types.response

import java.time.Instant

/**
 * Data class representing the latest known status of a member's registration.
 *
 * @param registrationId The registration request ID.
 * @param registrationSent Date when registration progress started. Null if not submitted.
 * @param registrationStatus Status of registration request: Submitted or not submitted.
 * @param memberInfoSubmitted Information sent to the MGM for registration.
 * @param reason Defined if the request has not submitted status, null by default.
 */
data class RegistrationRequestProgress(
    val registrationId: String,
    val registrationSent: Instant?,
    val registrationStatus: String,
    val memberInfoSubmitted: MemberInfoSubmitted,
    val reason: String?,
)
