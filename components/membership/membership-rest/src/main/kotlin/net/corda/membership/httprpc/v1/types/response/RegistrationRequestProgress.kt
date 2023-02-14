package net.corda.membership.httprpc.v1.types.response

import java.time.Instant

/**
 * Data class representing the latest known status of a member's registration.
 *
 * @param registrationId The registration request ID.
 * @param registrationSent Date when registration progress started. Null if not submitted.
 * @param registrationStatus Status of registration request: Submitted or not submitted.
 * @param availableNow False if the registration was submitted but might not be available in the APIs for a while.
 * @param reason Reason why the request has not submitted status. Has a default value, if the request has been submitted.
 * @param memberInfoSubmitted Information sent to the MGM for registration.
 */
data class RegistrationRequestProgress(
    val registrationId: String,
    val registrationSent: Instant?,
    val registrationStatus: String,
    val availableNow: Boolean,
    val reason: String,
    val memberInfoSubmitted: MemberInfoSubmitted,
)
