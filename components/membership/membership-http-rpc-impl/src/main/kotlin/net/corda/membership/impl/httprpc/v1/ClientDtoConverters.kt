package net.corda.membership.impl.httprpc.v1

import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.MemberRegistrationRequestDto
import net.corda.membership.client.dto.RegistrationActionDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.request.RegistrationAction
import net.corda.membership.httprpc.v1.types.response.MemberInfoSubmitted
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress

/**
 * Convert [MemberRegistrationRequest] from the HTTP API to the internal DTO [MemberRegistrationRequestDto].
 */
fun MemberRegistrationRequest.toDto() = MemberRegistrationRequestDto(
    holdingIdentityId,
    action.toDto()
)

/**
 * Convert [RegistrationAction] from the HTTP API to the internal DTO [RegistrationActionDto].
 */
fun RegistrationAction.toDto() = when(this) {
    RegistrationAction.REQUEST_JOIN -> RegistrationActionDto.REQUEST_JOIN
    else -> throw IllegalArgumentException("Unsupported registration action.")
}

/**
 * Convert internal DTO [RegistrationRequestProgressDto] to [RegistrationRequestProgress] from the HTTP API.
 */
fun RegistrationRequestProgressDto.fromDto() = RegistrationRequestProgress(
    registrationSent,
    registrationStatus,
    memberInfoSubmitted.fromDto()
)

/**
 * Convert internal DTO [MemberInfoSubmittedDto] to [MemberInfoSubmitted] from the HTTP API.
 */
fun MemberInfoSubmittedDto.fromDto() = MemberInfoSubmitted(data)