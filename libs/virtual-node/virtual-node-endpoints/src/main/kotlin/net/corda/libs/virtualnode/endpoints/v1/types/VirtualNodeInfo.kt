package net.corda.libs.virtualnode.endpoints.v1.types

import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import java.time.Instant
import java.util.UUID

data class VirtualNodeInfo(
    val holdingIdentity: HoldingIdentity,
    val cpiIdentifier: CpiIdentifier,
    /** Vault DDL DB connection ID */
    val vaultDdlConnectionId: UUID? = null,
    /** Vault DML DB connection ID */
    val vaultDmlConnectionId: UUID,
    /** Crypto DDL DB connection ID */
    val cryptoDdlConnectionId: UUID? = null,
    /** Crypto DML DB connection ID */
    val cryptoDmlConnectionId: UUID,
    /** HSM connection ID */
    val hsmConnectionId: UUID? = null,
    val state: String,
    /** Version of this vnode */
    val version: Int = -1,
    /** Creation timestamp */
    val timestamp: Instant,
)
