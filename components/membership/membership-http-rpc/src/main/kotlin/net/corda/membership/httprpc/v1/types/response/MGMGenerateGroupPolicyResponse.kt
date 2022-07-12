package net.corda.membership.httprpc.v1.types.response

import java.time.Instant

/**
 * Data class representing the latest known status of a member's registration.
 *
 * @param requestSent Date when generate group policy started. Null if not submitted.
 * @param groupPolicyStatus Status of generate group policy request.
 * @param memberInfoSubmitted Member Information returned.
 * @param mgmInfoSubmitted MGM Information returned.
 *
 */
class MGMGenerateGroupPolicyResponse (
    val requestSent: Instant?,
    val groupPolicyStatus: String,
    val memberInfoSubmitted: MemberInfoSubmitted,
    val mgmInfoSubmitted: MemberInfoSubmitted
)