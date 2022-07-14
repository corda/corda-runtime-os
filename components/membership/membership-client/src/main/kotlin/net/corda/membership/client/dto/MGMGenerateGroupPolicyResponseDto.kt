package net.corda.membership.client.dto

import java.time.Instant

/**
 * Data class representing the latest known status of generate group policy.
 *
 * @param requestSent Date when generate group policy started. Null if not submitted.
 * @param generatedGroupPolicy Generated group policy .
 */
data class MGMGenerateGroupPolicyResponseDto(
    val requestSent: Instant?,
    val generatedGroupPolicy: String
)
