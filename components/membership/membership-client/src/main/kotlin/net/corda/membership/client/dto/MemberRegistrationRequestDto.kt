package net.corda.membership.client.dto

/**
 * Request sent during member registration.
 *
 * @param holdingIdentityShortHash The ID of the holding identity the member is using.
 * @param action The action to take during registration.
 * @param context The member or MGM context required for on-boarding within a group.
 */
data class MemberRegistrationRequestDto(
    val holdingIdentityShortHash: String,
    val action: RegistrationActionDto,
    val context: Map<String, String>,
)
