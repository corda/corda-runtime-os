package net.corda.virtualnode

import net.corda.libs.packaging.core.CpiIdentifier
import java.time.Instant
import java.util.UUID

/**
 * Contains information relevant to a particular virtual node (a CPI and a holding identity).
 *
 * NOTE:  this object should contain information that does NOT require the full construction and instantiation of a CPI,
 * and is not specific to a particular CPI (e.g. a custom serializer, or custom crypto)
 *
 * This is intended to be returned (initially, and primarily) by the VirtualNodeInfoService which is a 'fast lookup' and
 * does NOT instantiate CPIs.
 *
 * Also see https://github.com/corda/platform-eng-design/blob/mnesbit-rpc-apis/core/corda-5/corda-5.1/rpc-apis/rpc_api.md#cluster-database
 */
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
    /** Uniqueness DDL DB connection ID */
    val uniquenessDdlConnectionId: UUID? = null,
    /** Uniqueness DML DB connection ID */
    val uniquenessDmlConnectionId: UUID,
    /** HSM connection ID */
    val hsmConnectionId: UUID? = null,
    /** Current state of the virtual node instance */
    val flowP2pOperationalStatus: OperationalStatus = DEFAULT_INITIAL_STATE,
    /** Current state of the virtual node instance */
    val flowStartOperationalStatus: OperationalStatus = DEFAULT_INITIAL_STATE,
    /** Current state of the virtual node instance */
    val flowOperationalStatus: OperationalStatus = DEFAULT_INITIAL_STATE,
    /** Current state of the virtual node instance */
    val vaultDbOperationalStatus: OperationalStatus = DEFAULT_INITIAL_STATE,
    /** Populated when an operation is in progress on this virtual node */
    val operationInProgress: String? = null,
    /** Version of this vnode */
    val version: Int = -1,
    /** Creation timestamp */
    val timestamp: Instant,
    /* Soft deleted */
    val isDeleted: Boolean = false,
) {
    companion object {
        val DEFAULT_INITIAL_STATE = OperationalStatus.ACTIVE
    }
}


typealias VirtualNodeInfoAvro = net.corda.data.virtualnode.VirtualNodeInfo

fun VirtualNodeInfo.toAvro(): VirtualNodeInfoAvro =
    with (holdingIdentity) {
        VirtualNodeInfoAvro(
            toAvro(),
            cpiIdentifier.toAvro(),
            vaultDdlConnectionId?.let{ vaultDdlConnectionId.toString() },
            vaultDmlConnectionId.toString(),
            cryptoDdlConnectionId?.let{ cryptoDdlConnectionId.toString() },
            cryptoDmlConnectionId.toString(),
            uniquenessDdlConnectionId?.let{ uniquenessDdlConnectionId.toString() },
            uniquenessDmlConnectionId.toString(),
            hsmConnectionId?.let { hsmConnectionId.toString() },
            flowP2pOperationalStatus.toString(),
            flowStartOperationalStatus.toString(),
            flowOperationalStatus.toString(),
            vaultDbOperationalStatus.toString(),
            operationInProgress,
            version,
            timestamp
        )
    }

fun VirtualNodeInfoAvro.toCorda(): VirtualNodeInfo {
    val holdingIdentity = holdingIdentity.toCorda()
    return VirtualNodeInfo(
        holdingIdentity,
        CpiIdentifier.fromAvro(cpiIdentifier),
        vaultDdlConnectionId?.let { UUID.fromString(vaultDdlConnectionId) },
        UUID.fromString(vaultDmlConnectionId),
        cryptoDdlConnectionId?.let { UUID.fromString(cryptoDdlConnectionId) },
        UUID.fromString(cryptoDmlConnectionId),
        uniquenessDdlConnectionId?.let { UUID.fromString(uniquenessDdlConnectionId) },
        UUID.fromString(uniquenessDmlConnectionId),
        hsmConnectionId?.let { UUID.fromString(hsmConnectionId) },
        OperationalStatus.valueOf(flowP2pOperationalStatus),
        OperationalStatus.valueOf(flowStartOperationalStatus),
        OperationalStatus.valueOf(flowOperationalStatus),
        OperationalStatus.valueOf(vaultDbOperationalStatus),
        operationInProgress,
        version,
        timestamp,
        false
    )
}

enum class OperationalStatus {
    ACTIVE,
    INACTIVE
}
