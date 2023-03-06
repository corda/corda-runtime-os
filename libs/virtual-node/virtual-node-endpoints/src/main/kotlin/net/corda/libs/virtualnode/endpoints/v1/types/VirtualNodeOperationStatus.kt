package net.corda.libs.virtualnode.endpoints.v1.types

import java.time.Instant

data class VirtualNodeOperationStatus(
    val requestId: String,
    val operationData: String,
    val requestTimestamp: Instant,
    val latestUpdateTimestamp: Instant,
    val heartbeatTimestamp: Instant?,
    val state: String,
    val errors: String?
)