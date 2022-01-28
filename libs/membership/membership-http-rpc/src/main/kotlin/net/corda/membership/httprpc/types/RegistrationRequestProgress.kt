package net.corda.membership.httprpc.types

data class RegistrationRequestProgress(
    /** Date when registration progress started. */
    val registrationSent: String,
    /** Status of registration request: Submitted or not submitted. */
    val registrationStatus: String,
    /** Information sent to the MGM for registration. */
    val memberInfoSubmitted: MemberInfoSubmitted
)
