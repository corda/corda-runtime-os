package net.corda.membership.client.dto

import java.time.Instant

/**
 * Data class representing the latest known status of generate group policy.
 *
 * @param requestSent Date when generate group policy started. Null if not submitted.
 * @param groupPolicyStatus Status of generate group policy request.
 * @param memberInfoSubmitted Member Information returned.
 * @param mgmInfoSubmitted MGM Information returned.
 */
data class MGMGenerateGroupPolicyResponseDto(
    val requestSent: Instant?,
    val groupPolicyStatus: String,
    val memberInfoSubmitted: MemberInfoSubmittedDto,
    val mgmInfoSubmitted: MemberInfoSubmittedDto
)
