package net.corda.libs.virtualnode.endpoints.v1.types

class UpgradeVirtualNodeStatus(
    val virtualNodeShortHash: String,
    val cpiFileChecksum: String,
    val stage: String?,
    startTime: Instant,
    endTime: Instant?,
    status: AsyncOperationStatus,
    errors: List<AsyncError>?
) : AsyncResponseStatus(startTime, endTime, status, errors)