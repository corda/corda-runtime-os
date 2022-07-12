package net.corda.membership.impl.httprpc.v1

import net.corda.membership.client.dto.*
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.MemberInfoSubmitted
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress

/**
 * Convert [MemberRegistrationRequest] from the HTTP API to the internal DTO [MemberRegistrationRequestDto].
 */
fun MemberRegistrationRequest.toDto() = MemberRegistrationRequestDto(
    holdingIdentityId,
    RegistrationActionDto.REQUEST_JOIN.getFromValue(action),
    context
)

/**
 * Convert internal DTO [RegistrationRequestProgressDto] to [RegistrationRequestProgress] from the HTTP API.
 */
fun RegistrationRequestProgressDto.fromDto() = RegistrationRequestProgress(
    registrationSent,
    registrationStatus,
    memberInfoSubmitted.fromDto()
)

/**
 * Convert internal DTO [MGMGenerateGroupPolicyResponseDto] to [MGMGenerateGroupPolicyResponse] from the HTTP API.
 */
fun MGMGenerateGroupPolicyResponseDto.fromDto() = MGMGenerateGroupPolicyResponse(
    requestSent,
    groupPolicyStatus,
    memberInfoSubmitted.fromDto(),
    mgmInfoSubmitted.fromDto()
)

/**
 * Convert internal DTO [MemberInfoSubmittedDto] to [MemberInfoSubmitted] from the HTTP API.
 */
fun MemberInfoSubmittedDto.fromDto() = MemberInfoSubmitted(data)
