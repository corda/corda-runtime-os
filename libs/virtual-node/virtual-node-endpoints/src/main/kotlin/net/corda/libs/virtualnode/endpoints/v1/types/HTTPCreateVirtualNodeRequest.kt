package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object sent via HTTP to request the creation of a virtual node.
 *
 * Exactly one of [cpiId] and [cpiIdHash] should be null.
 *
 * @param x500Name The X500 name for the new virtual node.
 * @param cpiId The long identifier of the CPI the virtual node is being created for.
 * @param cpiIdHash The short identifier of the CPI the virtual node is being created for.
 */
data class HTTPCreateVirtualNodeRequest(
    val x500Name: String,
    val cpiId: CpiIdentifier?,
    val cpiIdHash: String?
    // TODO - Add optional crypto DB and vault DB connection strings.
)