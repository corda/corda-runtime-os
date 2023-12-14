package net.corda.virtualnode

import net.corda.data.virtualnode.VirtualNodeOperationalState
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
    val uniquenessDmlConnectionId: UUID? = null,
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
    /** The requestId of an operation that is in progress on this virtual node. Null if no operation is in progress */
    val operationInProgress: String? = null,
    /** The route configuration for the external messaging. Null if no configuration was provided.*/
    val externalMessagingRouteConfig: String? = null,
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
    with(holdingIdentity) {
        VirtualNodeInfoAvro.newBuilder()
            .setHoldingIdentity(toAvro())
            .setCpiIdentifier(cpiIdentifier.toAvro())
            .setVaultDdlConnectionId(vaultDdlConnectionId?.let { vaultDdlConnectionId.toString() })
            .setVaultDmlConnectionId(vaultDmlConnectionId.toString())
            .setCryptoDdlConnectionId(cryptoDdlConnectionId?.let { cryptoDdlConnectionId.toString() })
            .setCryptoDmlConnectionId(cryptoDmlConnectionId.toString())
            .setUniquenessDdlConnectionId(uniquenessDdlConnectionId?.let { uniquenessDdlConnectionId.toString() })
            .setUniquenessDmlConnectionId(uniquenessDmlConnectionId?.let { uniquenessDmlConnectionId.toString() })
            .setHsmConnectionId(hsmConnectionId?.let { hsmConnectionId.toString() })
            .setFlowP2pOperationalStatus(flowP2pOperationalStatus.toAvro())
            .setFlowStartOperationalStatus(flowStartOperationalStatus.toAvro())
            .setFlowOperationalStatus(flowOperationalStatus.toAvro())
            .setVaultDbOperationalStatus(vaultDbOperationalStatus.toAvro())
            .setOperationInProgress(operationInProgress)
            .setExternalMessagingRouteConfig(externalMessagingRouteConfig)
            .setVersion(version)
            .setTimestamp(timestamp)
            .build()
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
        uniquenessDmlConnectionId?.let { UUID.fromString(uniquenessDmlConnectionId) },
        hsmConnectionId?.let { UUID.fromString(hsmConnectionId) },
        OperationalStatus.fromAvro(flowP2pOperationalStatus),
        OperationalStatus.fromAvro(flowStartOperationalStatus),
        OperationalStatus.fromAvro(flowOperationalStatus),
        OperationalStatus.fromAvro(vaultDbOperationalStatus),
        operationInProgress,
        externalMessagingRouteConfig,
        version,
        timestamp,
        false
    )
}

enum class OperationalStatus {
    ACTIVE,
    INACTIVE;

    companion object {
        fun fromAvro(status: VirtualNodeOperationalState): OperationalStatus {
            return when (status) {
                VirtualNodeOperationalState.ACTIVE -> ACTIVE
                VirtualNodeOperationalState.INACTIVE -> INACTIVE
            }
        }
    }

    fun toAvro(): VirtualNodeOperationalState {
        return when (this) {
            ACTIVE -> VirtualNodeOperationalState.ACTIVE
            INACTIVE -> VirtualNodeOperationalState.INACTIVE
        }
    }
}
