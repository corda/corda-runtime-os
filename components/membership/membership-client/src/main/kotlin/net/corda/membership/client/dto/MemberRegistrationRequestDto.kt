package net.corda.membership.client.dto

/**
 * Request sent during member registration.
 *
 * @param virtualNodeId The ID of the virtual node the member is running on.
 * @param action The action to take during registration.
 */
data class MemberRegistrationRequestDto(
    val virtualNodeId: String,
    val action: RegistrationActionDto
)