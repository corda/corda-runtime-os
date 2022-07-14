package net.corda.membership.httprpc.v1.types.response

import java.time.Instant

/**
 * Data class representing the latest known status of a member's registration.
 *
 * @param requestSent Date when generate group policy started. Null if not submitted.
 * @param generatedGroupPolicy Generated group policy.
 *
 */
class MGMGenerateGroupPolicyResponse (
    val requestSent: Instant?,
    val generatedGroupPolicy: String
)