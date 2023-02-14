package net.corda.libs.virtualnode.endpoints.v1.types

import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.AsynchronousOperationState
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