package net.corda.membership.client.dto

import net.corda.crypto.core.ShortHash

/**
 * Request sent during member registration.
 *
 * @param holdingIdentityShortHash The ID of the holding identity the member is using.
 * @param action The action to take during registration.
 * @param context The member or MGM context required for on-boarding within a group.
 */
data class MemberRegistrationRequestDto(
    val holdingIdentityShortHash: ShortHash,
    val action: RegistrationActionDto,
    val context: Map<String, String>,
)
