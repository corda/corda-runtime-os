package net.corda.membership.rest.v1.types.request

/**
 * Request sent during member registration.
 *
 * @param action The action to take during registration.
 * @param context The member or MGM context required for on-boarding within a group.
 */
data class MemberRegistrationRequest(
    val action: String,
    val context: Map<String, String>,
)
