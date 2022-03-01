package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object sent via HTTP to request the creation of a virtual node.
 *
 * @param x500Name The X500 name for the new virtual node.
 * @param cpiIdHash The short identifier of the CPI the virtual node is being created for.
 */
data class HTTPCreateVirtualNodeRequest(
    val x500Name: String,
    val cpiIdHash: String,
    val vaultDdlConnection: String?,
    val vaultDmlConnection: String?,
    val cryptoDdlConnection: String?,
    val cryptoDmlConnection: String?
)