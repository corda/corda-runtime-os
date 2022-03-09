package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object received via HTTP in response to a request to create a virtual node.
 *
 * Exactly one of [cpiId] and [cpiIdHash] should be null.
 *
 * @param x500Name The X500 name for the new virtual node.
 * @param cpiId The long identifier of the CPI the virtual node is being created for.
 * @param cpiIdHash The short identifier of the CPI the virtual node is being created for.
 * @param mgmGroupId The identifier of the CPI's MGM.
 * @param holdingIdHash The holding identifier for the virtual node.
 * @param vaultDdlConnectionId The ID of the connection for DDL operations in virtual node's vault database.
 * @param vaultDmlConnectionId The ID of the connection for DML operations in virtual node's vault database.
 * @param cryptoDdlConnectionId The ID of the connection for DDL operations in virtual node's crypto database.
 * @param cryptoDmlConnectionId The ID of the connection for DML operations in virtual node's crypto database.
 */
data class HTTPCreateVirtualNodeResponse(
    val x500Name: String,
    val cpiId: CPIIdentifier,
    val cpiIdHash: String?,
    val mgmGroupId: String,
    val holdingIdHash: String,
    val vaultDdlConnectionId: String? = null,
    val vaultDmlConnectionId: String,
    val cryptoDdlConnectionId: String? = null,
    val cryptoDmlConnectionId: String
)