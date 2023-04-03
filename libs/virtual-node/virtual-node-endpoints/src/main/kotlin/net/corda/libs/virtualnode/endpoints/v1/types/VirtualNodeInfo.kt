package net.corda.libs.virtualnode.endpoints.v1.types

import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.external.messaging.entities.RouteConfiguration
import net.corda.virtualnode.OperationalStatus

/**
 * This class is serialized and returned as JSON in the REST api
 *
 * These field names are what the end-users see.
 */
/**
 * Virtual Node Info
 *
 * @param holdingIdentity The holding identity of this virtual node.
 * @param cpiIdentifier The CPI that this virtual node uses.
 * @param vaultDdlConnectionId Vault DB DDL connection ID.
 * @param vaultDmlConnectionId Vault DB DML connection ID.
 * @param cryptoDdlConnectionId Crypto DB DDL connection ID.
 * @param cryptoDmlConnectionId Crypto DB DML connection ID.
 * @param uniquenessDdlConnectionId Uniqueness DB DDL connection ID.
 * @param uniquenessDmlConnectionId Uniqueness DB DML connection ID.
 * @param hsmConnectionId HSM connection ID.
 * @param flowP2pOperationalStatus operational status of the virtual node.
 * @param flowStartOperationalStatus operational status of the virtual node.
 * @param flowOperationalStatus operational status of the virtual node.
 * @param vaultDbOperationalStatus operational status of the virtual node.
 * @param operationInProgress ID of any ongoing operation on this virtual node.
 * @param externalMessagingRouteConfiguration The external messaging route configuration for the virtual node.
 */
data class VirtualNodeInfo(
    val holdingIdentity: HoldingIdentity,
    val cpiIdentifier: CpiIdentifier,
    val vaultDdlConnectionId: String? = null,
    val vaultDmlConnectionId: String,
    val cryptoDdlConnectionId: String? = null,
    val cryptoDmlConnectionId: String,
    val uniquenessDdlConnectionId: String? = null,
    val uniquenessDmlConnectionId: String,
    val hsmConnectionId: String? = null,
    val flowP2pOperationalStatus: OperationalStatus,
    val flowStartOperationalStatus: OperationalStatus,
    val flowOperationalStatus: OperationalStatus,
    val vaultDbOperationalStatus: OperationalStatus,
    val operationInProgress: String? = null,
    val externalMessagingRouteConfiguration: RouteConfiguration? = null
)
