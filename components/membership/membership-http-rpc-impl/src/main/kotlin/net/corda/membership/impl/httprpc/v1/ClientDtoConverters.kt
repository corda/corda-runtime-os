package net.corda.membership.impl.httprpc.v1

import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.MemberRegistrationRequestDto
import net.corda.membership.client.dto.RegistrationActionDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.MemberInfoSubmitted
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestStatus
import net.corda.membership.httprpc.v1.types.response.RegistrationStatus
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow

/**
 * Convert [MemberRegistrationRequest] from the HTTP API to the internal DTO [MemberRegistrationRequestDto].
 */
fun MemberRegistrationRequest.toDto(holdingIdentityShortHash: String) = MemberRegistrationRequestDto(
    ShortHash.parseOrThrow(holdingIdentityShortHash),
    RegistrationActionDto.REQUEST_JOIN.getFromValue(action),
    context
)

/**
 * Convert internal DTO [RegistrationRequestProgressDto] to [RegistrationRequestProgress] from the HTTP API.
 */
fun RegistrationRequestProgressDto.fromDto() = RegistrationRequestProgress(
    registrationRequestId,
    registrationSent,
    registrationStatus.toString(),
    reason,
    memberInfoSubmitted.fromDto()
)

/**
 * Convert internal DTO [MemberInfoSubmittedDto] to [MemberInfoSubmitted] from the HTTP API.
 */
fun MemberInfoSubmittedDto.fromDto() = MemberInfoSubmitted(data)

fun RegistrationRequestStatusDto.fromDto() = RegistrationRequestStatus(
    this.registrationId,
    this.registrationSent,
    this.registrationUpdated,
    this.registrationStatus.fromDto(),
    this.memberInfoSubmitted.fromDto(),
)

fun RegistrationStatusDto.fromDto() = when (this) {
    RegistrationStatusDto.NEW -> RegistrationStatus.NEW
    RegistrationStatusDto.SENT_TO_MGM -> RegistrationStatus.SENT_TO_MGM
    RegistrationStatusDto.RECEIVER_BY_MGM -> RegistrationStatus.RECEIVER_BY_MGM
    RegistrationStatusDto.PENDING_MEMBER_VERIFICATION -> RegistrationStatus.PENDING_MEMBER_VERIFICATION
    RegistrationStatusDto.PENDING_APPROVAL_FLOW -> RegistrationStatus.PENDING_APPROVAL_FLOW
    RegistrationStatusDto.PENDING_MANUAL_APPROVAL -> RegistrationStatus.PENDING_MANUAL_APPROVAL
    RegistrationStatusDto.PENDING_AUTO_APPROVAL -> RegistrationStatus.PENDING_AUTO_APPROVAL
    RegistrationStatusDto.DECLINED -> RegistrationStatus.DECLINED
    RegistrationStatusDto.INVALID -> RegistrationStatus.INVALID
    RegistrationStatusDto.APPROVED -> RegistrationStatus.APPROVED
}