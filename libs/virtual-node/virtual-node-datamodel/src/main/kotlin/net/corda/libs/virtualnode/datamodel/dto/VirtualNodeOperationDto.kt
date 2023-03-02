package net.corda.libs.virtualnode.datamodel.dto

import java.time.Instant

/**
 * DTO to capture virtual node operations.
 */
data class VirtualNodeOperationDto(
    val requestId: String,
    val requestData: String,
    val operationType: String,
    val requestTimestamp: Instant,
    val latestUpdateTimestamp: Instant?,
    val heartbeatTimestamp: Instant?,
    val state: String,
    val errors: String?
)