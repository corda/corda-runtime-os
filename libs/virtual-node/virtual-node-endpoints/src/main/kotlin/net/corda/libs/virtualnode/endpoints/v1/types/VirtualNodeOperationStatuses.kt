package net.corda.libs.virtualnode.endpoints.v1.types

data class VirtualNodeOperationStatuses(
    val requestId: String,
    val response: List<VirtualNodeOperationStatus>
)