package net.corda.membership.impl.rest.v1

import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.rest.v1.types.response.MemberInfoSubmitted
import net.corda.membership.rest.v1.types.response.RegistrationRequestProgress
import net.corda.membership.rest.v1.types.response.RegistrationStatus
import net.corda.membership.rest.v1.types.response.RestRegistrationRequestStatus

/**
 * Convert internal DTO [RegistrationRequestProgressDto] to [RegistrationRequestProgress] from the HTTP API.
 */
fun RegistrationRequestProgressDto.fromDto() = RegistrationRequestProgress(
    registrationRequestId,
    registrationSent,
    registrationStatus.toString(),
    availableNow,
    reason,
    memberInfoSubmitted.fromDto()
)

/**
 * Convert internal DTO [MemberInfoSubmittedDto] to [MemberInfoSubmitted] from the HTTP API.
 */
fun MemberInfoSubmittedDto.fromDto() = MemberInfoSubmitted(data)

fun RegistrationRequestStatusDto.fromDto() = RestRegistrationRequestStatus(
    this.registrationId,
    this.registrationSent,
    this.registrationUpdated,
    this.registrationStatus.fromDto(),
    this.memberInfoSubmitted.fromDto(),
    this.reason,
    this.serial,
)

fun RegistrationStatusDto.fromDto() = when (this) {
    RegistrationStatusDto.NEW -> RegistrationStatus.NEW
    RegistrationStatusDto.SENT_TO_MGM -> RegistrationStatus.SENT_TO_MGM
    RegistrationStatusDto.RECEIVED_BY_MGM -> RegistrationStatus.RECEIVED_BY_MGM
    RegistrationStatusDto.STARTED_PROCESSING_BY_MGM -> RegistrationStatus.STARTED_PROCESSING_BY_MGM
    RegistrationStatusDto.PENDING_MEMBER_VERIFICATION -> RegistrationStatus.PENDING_MEMBER_VERIFICATION
    RegistrationStatusDto.PENDING_MANUAL_APPROVAL -> RegistrationStatus.PENDING_MANUAL_APPROVAL
    RegistrationStatusDto.PENDING_AUTO_APPROVAL -> RegistrationStatus.PENDING_AUTO_APPROVAL
    RegistrationStatusDto.DECLINED -> RegistrationStatus.DECLINED
    RegistrationStatusDto.INVALID -> RegistrationStatus.INVALID
    RegistrationStatusDto.FAILED -> RegistrationStatus.FAILED
    RegistrationStatusDto.APPROVED -> RegistrationStatus.APPROVED
}