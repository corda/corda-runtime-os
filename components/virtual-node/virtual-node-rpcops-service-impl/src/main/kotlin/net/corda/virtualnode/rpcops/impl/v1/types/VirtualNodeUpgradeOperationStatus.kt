package net.corda.virtualnode.rpcops.impl.v1.types

data class VirtualNodeUpgradeOperationStatus(
    val requestId: String,
    val originalCpiFileChecksum: String,
    val targetCpiFileChecksum: String,
    val virtualNodeShortHash: String,
    val actor: String,
    val stage: String
)