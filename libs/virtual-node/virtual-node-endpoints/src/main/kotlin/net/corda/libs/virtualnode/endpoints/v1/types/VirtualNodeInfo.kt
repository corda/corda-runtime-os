package net.corda.libs.virtualnode.endpoints.v1.types

import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier

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
    val flowP2pOperationalStatus: String,
    val flowStartOperationalStatus: String,
    val flowOperationalStatus: String,
    val vaultDbOperationalStatus: String,
    val operationInProgress: String? = null
)
