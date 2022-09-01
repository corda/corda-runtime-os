package net.corda.libs.virtualnode.endpoints.v1.types

import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier

/**
 * This class is serialized and returned as JSON in the REST api
 *
 * These field names are what the end-users see.
 */
data class VirtualNodeInfo(
    /** The holding identity of this virtual node */
    val holdingIdentity: HoldingIdentity,
    /** The CPI that this virtual node uses */
    val cpiIdentifier: CpiIdentifier,
    /** Vault DDL DB connection ID */
    val vaultDdlConnectionId: String? = null,
    /** Vault DML DB connection ID */
    val vaultDmlConnectionId: String,
    /** Crypto DDL DB connection ID */
    val cryptoDdlConnectionId: String? = null,
    /** Crypto DML DB connection ID */
    val cryptoDmlConnectionId: String,
    /** Uniqueness DDL DB connection ID */
    val uniquenessDdlConnectionId: String? = null,
    /** Uniqueness DML DB connection ID */
    val uniquenessDmlConnectionId: String,
    /** HSM connection ID */
    val hsmConnectionId: String? = null,
    /** The virtual node state */
    val state: String,
)
