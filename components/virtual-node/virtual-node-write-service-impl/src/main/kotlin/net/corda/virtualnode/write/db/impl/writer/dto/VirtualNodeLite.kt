package net.corda.virtualnode.write.db.impl.writer.dto

import java.time.Instant

data class VirtualNodeLite(
    val holdingIdentityShortHash: String,
    val holdingIdentity: HoldingIdentityLite,
    val cpiName: String,
    val cpiVersion: String,
    val cpiSignerSummaryHash: String,
    val flowP2pOperationalStatus: String,
    val flowStartOperationalStatus: String,
    val flowOperationalStatus: String,
    val vaultDbOperationalStatus: String,
    val vaultDdlConnectionId: String?,
    val vaultDmlConnectionId: String?,
    val cryptoDdlConnectionId: String?,
    val cryptoDmlConnectionId: String?,
    val uniquenessDdlConnectionId: String?,
    val uniquenessDmlConnectionId: String?,
    val operationInProgress: String?,
    val entityVersion: Int,
    val creationTimestamp: Instant?
)