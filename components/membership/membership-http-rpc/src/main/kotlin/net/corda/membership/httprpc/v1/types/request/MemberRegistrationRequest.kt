package net.corda.membership.httprpc.v1.types.request

/**
 * Request sent during member registration.
 *
 * @param holdingIdentityId The ID of the holding identity the member is using.
 * @param action The action to take during registration.
 * @param context The member or MGM context required for on-boarding within a group.
 */
data class MemberRegistrationRequest(
    val holdingIdentityId: String,
    val action: RegistrationAction,
    val context: Map<String, String>,
)