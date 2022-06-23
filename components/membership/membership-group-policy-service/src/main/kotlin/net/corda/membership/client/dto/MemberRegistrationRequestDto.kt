package net.corda.membership.client.dto

/**
 * Request sent during member registration.
 *
 * @param holdingIdentityId The ID of the holding identity the member is using.
 * @param action The action to take during registration.
 */
data class MemberRegistrationRequestDto(
    val holdingIdentityId: String,
    val action: RegistrationActionDto
)